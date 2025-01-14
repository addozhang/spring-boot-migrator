/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sbm.java.impl;

import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.*;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.Class;
import org.openrewrite.java.tree.TypeUtils;
import org.springframework.rewrite.parser.maven.ClasspathDependencies;
import org.springframework.rewrite.resource.RewriteSourceFileHolder;
import org.springframework.rewrite.recipes.GenericOpenRewriteRecipe;
import org.springframework.sbm.java.api.*;
import org.springframework.sbm.java.migration.visitor.RemoveImplementsVisitor;
import org.springframework.sbm.java.refactoring.JavaRefactoring;
import org.springframework.rewrite.parser.JavaParserBuilder;
import org.springframework.sbm.support.openrewrite.java.FindCompilationUnitContainingType;
import org.springframework.sbm.support.openrewrite.java.RemoveAnnotationVisitor;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class OpenRewriteType implements Type {

    private final UUID classDeclId;

    private final RewriteSourceFileHolder<J.CompilationUnit> rewriteSourceFileHolder;

    private final JavaRefactoring refactoring;
    private final ClassDeclaration classDeclaration;
    private final ExecutionContext executionContext;
    private JavaParserBuilder javaParserBuilder;

    public OpenRewriteType(ClassDeclaration classDeclaration, RewriteSourceFileHolder<J.CompilationUnit> rewriteSourceFileHolder, JavaRefactoring refactoring, ExecutionContext executionContext, JavaParserBuilder javaParserBuilder) {
        this.classDeclId = classDeclaration.getId();
        this.classDeclaration = classDeclaration;
        this.rewriteSourceFileHolder = rewriteSourceFileHolder;
        this.refactoring = refactoring;
        this.executionContext = executionContext;
        this.javaParserBuilder = javaParserBuilder;
    }

    public List<OpenRewriteMember> getMembers() {
        return Utils.getFields(getClassDeclaration()).stream()
                .flatMap(variableDecls -> createMembers(refactoring, variableDecls))
                .collect(Collectors.toList());
    }

    private Stream<OpenRewriteMember> createMembers(JavaRefactoring refactoring, J.VariableDeclarations variableDecls) {
        return variableDecls.getVariables().stream()
                .map(namedVar -> new OpenRewriteMember(variableDecls, namedVar, rewriteSourceFileHolder, refactoring, javaParserBuilder));
    }

    @Override
    public String getSimpleName() {
        return getClassDeclaration().getSimpleName();
    }

    @Override
    public String getFullyQualifiedName() {
        return TypeUtils.asFullyQualified(getClassDeclaration().getType()).getFullyQualifiedName();
    }

    @Override
    public boolean hasAnnotation(String annotation) {
        return !findORAnnotations(annotation).isEmpty();
    }

    @Override
    public List<Annotation> findAnnotations(String annotation) {
        return findORAnnotations(annotation).stream()
                .map(a -> Wrappers.wrap(a, refactoring, javaParserBuilder)).collect(Collectors.toList());
    }

    @Override
    public List<Annotation> getAnnotations() {
        return getClassDeclaration().getLeadingAnnotations().stream()
                .map(a -> new OpenRewriteAnnotation(a, refactoring, javaParserBuilder))
                .collect(Collectors.toList());
    }

    @Override
    public void addAnnotation(String fqName) {
        String snippet = "@" + fqName.substring(fqName.lastIndexOf('.') + 1);
        addAnnotation(snippet, fqName);
    }

    /*
     * TODO: Does JavaParser update typesInUse?
     */
    @Override
    public void addAnnotation(String snippet, String annotationImport, String... otherImports) {
        // FIXME: The ClasspathDependencies Marker makes this code incompatible to OpenRewrite
        Optional<ClasspathDependencies> classpathDependencies = rewriteSourceFileHolder.getSourceFile().getMarkers().findFirst(ClasspathDependencies.class);
        List<Path> classpath = classpathDependencies.get().getDependencies();

        GenericOpenRewriteRecipe<JavaIsoVisitor<ExecutionContext>> recipe = new GenericOpenRewriteRecipe<>(() -> {
            return new JavaIsoVisitor<>() {
                @Override
                public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext executionContext) {
                    ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                    if (cd == getClassDeclaration()) {
                        List<String> imports = Stream.concat(Stream.of(otherImports), Stream.of(annotationImport)).toList();
                        // FIXME: Requires stubs for JAX-RS instead of types from jar
                        JavaTemplate javaTemplate = JavaTemplate.builder(snippet).imports(imports.toArray(String[]::new)).javaParser(javaParserBuilder.classpath(classpath).clone()).build();
                        cd = javaTemplate.apply(getCursor(), getClassDeclaration().getCoordinates().addAnnotation((o1, o2) -> Integer.valueOf(o1.getSimpleName().length()).compareTo(o2.getSimpleName().length())));
                        imports.forEach(i -> maybeAddImport(i));
                    }
                    return cd;
                }
            };
        });
        refactoring.refactor(rewriteSourceFileHolder, recipe);
    }

    /**
     * Does not rely on markers to resolve classpath but takes a list of type stubs instead.
     */
    @Override
    public void addAnnotation(String snippet, String annotationImport, Set<String> typeStubs, String... otherImports) {
        // FIXME: The ClasspathDependencies Marker makes this code incompatible to OpenRewrite
        GenericOpenRewriteRecipe<JavaIsoVisitor<ExecutionContext>> recipe = new GenericOpenRewriteRecipe<>(() -> {
            return new JavaIsoVisitor<>() {
                @Override
                public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext executionContext) {
                    ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                    if (cd == getClassDeclaration()) {
                        List<String> imports = Stream.concat(Stream.of(otherImports), Stream.of(annotationImport)).toList();
                        // FIXME: Requires stubs for JAX-RS instead of types from jar
                        String[] classpath = typeStubs.toArray(String[]::new);
                        JavaTemplate javaTemplate = JavaTemplate.builder(snippet).imports(imports.toArray(String[]::new)).javaParser(javaParserBuilder.dependsOn(classpath).clone()).build();
                        cd = javaTemplate.apply(getCursor(), getClassDeclaration().getCoordinates().addAnnotation((o1, o2) -> Integer.valueOf(o1.getSimpleName().length()).compareTo(o2.getSimpleName().length())));
                        imports.forEach(i -> maybeAddImport(i));
                    }
                    return cd;
                }
            };
        });
        refactoring.refactor(rewriteSourceFileHolder, recipe);
    }

    @Override
    public Annotation getAnnotation(String fqName) {
        return getAnnotations().stream()
                .filter(a -> a.getFullyQualifiedName().equals(fqName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Type '" + getFullyQualifiedName() + "' has no annotation '" + fqName + "'."));
    }

    @Override
    // FIXME: reuse
    public void removeAnnotation(String fqName) {
        // TODO: See if RemoveAnnotationVisitor can be replaced with OpenRewrite's version
        Recipe removeAnnotationRecipe = new GenericOpenRewriteRecipe<>(() -> new RemoveAnnotationVisitor(getClassDeclaration(), fqName));
        refactoring.refactor(rewriteSourceFileHolder, removeAnnotationRecipe);
        refactoring.refactor(rewriteSourceFileHolder, new RemoveUnusedImports());
    }

    @Override
    public void removeAnnotation(Annotation annotation) {
        Recipe removeAnnotationRecipe = new GenericOpenRewriteRecipe<>(() -> new RemoveAnnotationVisitor(getClassDeclaration(), annotation.getFullyQualifiedName()));
        refactoring.refactor(rewriteSourceFileHolder, removeAnnotationRecipe);
        refactoring.refactor(rewriteSourceFileHolder, new RemoveUnusedImports());
    }

    @Override
    public List<Method> getMethods() {
        return Utils.getMethods(getClassDeclaration()).stream()
                .map(m -> new OpenRewriteMethod(rewriteSourceFileHolder, m, refactoring, javaParserBuilder, executionContext))
                .collect(Collectors.toList());
    }

    @Override
    public void addMethod(String methodTemplate, Set<String> requiredImports) {
        this.apply(new GenericOpenRewriteRecipe<>(() -> new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, executionContext);
                if (cu == rewriteSourceFileHolder.getSourceFile()) {
                    ClassDeclaration classDeclaration = cu.getClasses().stream()
                            .filter(cd -> cd == getClassDeclaration())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Could not find type matching current instance '%s'".formatted(getFullyQualifiedName())));

                    ClasspathDependencies classpathDependencies = cu.getMarkers().findFirst(ClasspathDependencies.class).get();
                    JavaParser.Builder clone = javaParserBuilder
                            .classpath(classpathDependencies.getDependencies())
                            .clone();

                    JavaTemplate template = JavaTemplate
                            .builder(methodTemplate)
                            .javaParser(clone)
                            .imports(requiredImports.toArray(new String[0]))
                            .build();

                    c = template.apply(getCursor(), classDeclaration.getBody().getCoordinates().lastStatement());
                    requiredImports.forEach(i -> maybeAddImport(i));
                }
                return c;
            }
        }));
    }

//            @Override
//            public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext executionContext) {
//                ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
//                J.CompilationUnit cu = getCursor().dropParentUntil(J.CompilationUnit.class::isInstance).getValue();
//                checkTypeAvailability(cu, requiredImports);
//
//                Markers markers = cu.getMarkers();
//                ClasspathDependencies classpathDependencies = markers.findFirst(ClasspathDependencies.class).get();
//
//                JavaTypeCache javaTypeCache = new JavaTypeCache();
//                JavaParser.Builder clone = javaParserBuilder
//                        .typeCache(javaTypeCache)
//                        .classpath(classpathDependencies.getDependencies())
//                        .clone();
//
//                JavaTemplate template = JavaTemplate
//                        .builder(methodTemplate)
//                        .javaParser(clone)
//                        .imports(requiredImports.toArray(new String[0]))
//                        .build();
//                cd = template.apply(getCursor(), cd.getBody().getCoordinates().lastStatement());
//
//                JavaSourceSet main = JavaSourceSet.build("main", classpathDependencies.getDependencies(), javaTypeCache, false);
//                markers = markers.removeByType(JavaSourceSet.class);
//                markers = markers.addIfAbsent(main);
//                ClassDeclaration classDeclaration1 = cd.withMarkers(markers);
//
//                boolean onlyIfReferenced = false;
//                requiredImports.forEach(i -> maybeAddImport(i, onlyIfReferenced));
//
//                return classDeclaration1;
//            }
//        }));
//        this.apply(new WrappingAndBraces());
//    }

    private void checkTypeAvailability(J.CompilationUnit cd, Set<String> requiredImports) {
        List<String> missingTypes = cd.getTypesInUse().getTypesInUse().stream()
                .map(JavaType::toString)
                .filter(t -> !requiredImports.contains(t))
                .toList();
        if (!missingTypes.isEmpty()) {
            throw new IllegalArgumentException("These types %s are not available to in compilation unit %s".formatted(missingTypes, cd.getSourcePath()));
        }
    }


    private List<J.Annotation> findORAnnotations(String annotation) {
        return getClassDeclaration().getLeadingAnnotations()
                .stream()
                .filter(a -> {
                    Object typeObject = a.getAnnotationType().getType();
                    String simpleName = a.getSimpleName();
                    if (JavaType.Unknown.class.isInstance(typeObject)) {
                        log.warn("Could not resolve Type for annotation: '" + simpleName + "' while comparing with '" + annotation + "'.");
                        return false;
                    } else if (JavaType.Class.class.isInstance(typeObject)) {
                        Class type = Class.class.cast(typeObject);
                        return annotation.equals(type.getFullyQualifiedName());
                    } else {
                        log.warn("Could not resolve Type for annotation: '" + simpleName + "' (" + typeObject + ") while comparing with '" + annotation + "'.");
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isTypeOf(String fqName) {
        return classDeclaration.getType().isAssignableTo(fqName);
    }

    @Override
    public List<? extends Type> getImplements() {
        ClassDeclaration classDeclaration = getClassDeclaration();
        if (classDeclaration.getType() == null) {
            throw new RuntimeException("Could not resolve type for classDeclaration '" + classDeclaration.getName() + "'");
        }

        List<? extends Type> types = classDeclaration.getType().getInterfaces().stream()
                .filter(jt -> JavaType.FullyQualified.class.isAssignableFrom(jt.getClass()))
                .map(JavaType.FullyQualified.class::cast)
                .map(this::extracted)
                .collect(Collectors.toList());

        return types;
    }

    private Type extracted(JavaType.FullyQualified javaType) {
        // TODO: maybe split Type into TypeInfo and Type (extending TypeInfo) to split Type in mutable and immutable api ?
        if (JavaType.FullyQualified.class.isAssignableFrom(javaType.getClass())) {
            JavaType.FullyQualified fullyQualified = JavaType.FullyQualified.class.cast(javaType);
            Optional<OpenRewriteType> openRewriteTypeOptional = buildForJavaType(fullyQualified);
            if (openRewriteTypeOptional.isPresent()) {
                return openRewriteTypeOptional.get();
            } else {
                if (JavaType.Class.class.isAssignableFrom(javaType.getClass())) {
                    JavaType.Class javaTypeClass = JavaType.Class.class.cast(javaType);
                    return new CompiledType(javaTypeClass);
                }
            }
        }
        throw new RuntimeException(String.format("Could not retrieve type for '%s'", javaType));
    }

    @Override
    public Optional<? extends Type> getExtends() {
        ClassDeclaration classDeclaration = getClassDeclaration();
        if (classDeclaration.getType() == null) {
            throw new RuntimeException("Could not resolve type for classDeclaration '" + classDeclaration.getName() + "'");
        }
        JavaType.FullyQualified extendings = classDeclaration.getType().getSupertype();
        if (extendings == null) {
            return Optional.empty();
        }
        return buildForJavaType(extendings);
    }

    private Optional<OpenRewriteType> buildForJavaType(JavaType.FullyQualified jt) {
        List<RewriteSourceFileHolder<J.CompilationUnit>> compilationUnits = refactoring.find(new FindCompilationUnitContainingType(jt));
        if (compilationUnits.isEmpty()) {
            return Optional.empty();
        }
        RewriteSourceFileHolder<J.CompilationUnit> modifiableCompilationUnit = compilationUnits.get(0);
        J.ClassDeclaration classDeclaration = modifiableCompilationUnit.getSourceFile().getClasses().stream()
                .peek(c -> {
                    if (c.getType() == null)
                        log.warn("Could not resolve type for class declaration '" + c.getName() + "'.");
                })
                .filter(c -> c.getType() != null)
                .filter(c -> c.getType().getFullyQualifiedName().equals(jt.getFullyQualifiedName().trim()))
                .findFirst()
                .orElseThrow();
        return Optional.of(new OpenRewriteType(classDeclaration, modifiableCompilationUnit, refactoring,
                executionContext, javaParserBuilder));
    }

    @Override
    public KindOfType getKind() {
        return KindOfType.valueOf(this.getClassDeclaration().getKind().name().toUpperCase());
    }

    @Override
    public void removeImplements(String... fqNames) {
        RemoveImplementsVisitor visitor = new RemoveImplementsVisitor(getClassDeclaration(), fqNames);
        refactoring.refactor(rewriteSourceFileHolder, visitor);
    }

    public J.ClassDeclaration getClassDeclaration() {
        return rewriteSourceFileHolder.getSourceFile().getClasses().stream()
                .filter(cd -> cd.getId().equals(classDeclId))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Could not get class declaration for type in '" + rewriteSourceFileHolder.getSourceFile().getSourcePath() + "' with ID '" + classDeclId + "'."));
    }

    @Override
    public String toString() {
        return getFullyQualifiedName();
    }

    @Override
    public void apply(Recipe... r) {
        refactoring.refactor(rewriteSourceFileHolder, r);
    }

    @Override
    public boolean hasMethod(String methodPattern) {
        // TODO: parse and validate methodPattern
        methodPattern = this.getFullyQualifiedName() + " " + methodPattern;
        DeclaresMethod<ExecutionContext> declaresMethod = new DeclaresMethod(new MethodMatcher(methodPattern, true));
        List<RewriteSourceFileHolder<J.CompilationUnit>> matches = refactoring.find(rewriteSourceFileHolder, new GenericOpenRewriteRecipe<>(() -> declaresMethod));
        // TODO: searches in all classes, either filter result list or provide findInCurrent() or similar
        return !matches.isEmpty();
    }

    @Override
    public Method getMethod(String methodPattern) {
        final String fullMethodPattern = this.getFullyQualifiedName() + " " + methodPattern;
        if (!hasMethod(methodPattern)) {
            throw new IllegalArgumentException(String.format("Type '%s' has no method matching pattern '%s'.", getFullyQualifiedName(), fullMethodPattern));
        }
        return getMethods().stream()
                // TODO: can getMethodDecl be made package private ?
                .filter(m -> new MethodMatcher(fullMethodPattern, true).matches(m.getMethodDecl(), this.classDeclaration))
                .findFirst().get();
    }

    /**
     * Add a non static member to this type
     * <p>
     * [source, java]
     * .....
     * import <type>;
     * class MyClass {
     * <visibilit> <type> <name>;
     * }
     * .....
     *
     * @param visibility of the member
     * @param type       the fully qualified type of the member
     * @param name       of the member
     */
    @Override
    public void addMember(Visibility visibility, String type, String name) {
        JavaIsoVisitor<ExecutionContext> javaIsoVisitor = new JavaIsoVisitor<>() {
            @Override
            public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext executionContext) {
                ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                JavaType javaType = JavaType.buildType(type);
                String className = ((JavaType.FullyQualified) javaType).getClassName();

                JavaTemplate javaTemplate = JavaTemplate.builder("@Autowired\n" + visibility.getVisibilityName() + " " + className + " " + name + ";")
                        .imports(type, "org.springframework.beans.factory.annotation.Autowired")
                        .javaParser(javaParserBuilder)
                        .build();

                maybeAddImport(type);
                maybeAddImport("org.springframework.beans.factory.annotation.Autowired");


                cd = javaTemplate.apply(getCursor().getParent(), cd.getBody().getCoordinates().firstStatement());
                return cd;
            }
        };
        apply(new GenericOpenRewriteRecipe<JavaIsoVisitor<ExecutionContext>>(() -> javaIsoVisitor));
    }

    public boolean isImplementing(String fqClassName) {
        return getClassDeclaration()
                .getType()
                .getInterfaces()
                .stream()
                .map(JavaType.FullyQualified::getFullyQualifiedName)
                .anyMatch(fqClassName::equals);
    }

}
