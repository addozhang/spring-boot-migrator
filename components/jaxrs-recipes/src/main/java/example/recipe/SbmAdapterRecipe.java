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
package example.recipe;


import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.springframework.rewrite.parser.JavaParserBuilder;
import org.springframework.rewrite.resource.*;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.engine.context.ProjectContextFactory;
import org.springframework.sbm.java.JavaSourceProjectResourceWrapper;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactory;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactoryImpl;
import org.springframework.sbm.java.util.BasePackageCalculator;
import org.springframework.sbm.jee.jaxrs.actions.ConvertJaxRsAnnotations;
import org.springframework.sbm.project.resource.ProjectResourceSetHolder;
import org.springframework.sbm.project.resource.ProjectResourceWrapper;
import org.springframework.sbm.project.resource.ProjectResourceWrapperRegistry;
import org.springframework.sbm.project.resource.SbmApplicationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class SbmAdapterRecipe extends ScanningRecipe<List<SourceFile>> {

    private ProjectResourceSetFactory projectResourceSetFactory;
    private ProjectContextFactory projectContextFactory;

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "";
    }

    public SbmAdapterRecipe() {

    }

    @Override
    public List<SourceFile> getInitialValue(ExecutionContext ctx) {
        return new ArrayList<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(List<SourceFile> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if(tree instanceof SourceFile) {
                    acc.add((SourceFile) tree);
                }
                return super.visit(tree, executionContext);
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(List<SourceFile> acc, ExecutionContext executionContext) {
        Collection<? extends SourceFile> generate = super.generate(acc, executionContext);

        // create the required classes
        initBeans(executionContext);

        // transform nodes to SourceFiles
        List<SourceFile> sourceFiles = acc.stream().filter(SourceFile.class::isInstance).map(SourceFile.class::cast).toList();

        // FIXME: base dir calculation is fake
        Path baseDir = Path.of("/Users/fkrueger/projects/spring-boot-migrator/components/jaxrs-recipes/testcode/jee/jaxrs/bootify-jaxrs/given").toAbsolutePath().normalize(); //executionContext.getMessage("base.dir");

        // Create the SBM resource set abstraction
        ProjectResourceSet projectResourceSet = projectResourceSetFactory.create(baseDir, sourceFiles);
        // Create the SBM ProjectContext
        ProjectContext pc = projectContextFactory.createProjectContext(baseDir, projectResourceSet);

        // Execute the SBM Action = the JAXRS Recipe
        new ConvertJaxRsAnnotations().apply(pc);

        // Merge back result
        List<? extends SourceFile> modifiedNodes = merge(sourceFiles, pc.getProjectResources());

        return modifiedNodes;
    }

    //    @Override
//    public List<Recipe> getRecipeList() {
//        List<Recipe> recipeList = new ArrayList<>();
//        recipeList.add(new GenericOpenRewriteRecipe<>(() -> new TreeVisitor<Tree, ExecutionContext>() {
//            @Override
//            public void visit(@Nullable List<? extends Tree> nodes, ExecutionContext executionContext) {
//                super.visit(nodes, executionContext);
//            }
//
//            @Override
//            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
//                return super.visit(tree, executionContext);
//            }
//
//            @Override
//            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext, Cursor parent) {
//                return super.visit(tree, executionContext, parent);
//            }
//        }));
//        return recipeList;
//    }

//    @Override
//    public TreeVisitor<?, ExecutionContext> getVisitor() {
//        return new TreeVisitor<Tree, ExecutionContext>() {
//
//            private ProjectResourceSetFactory projectResourceSetFactory;
//            private ProjectContextFactory projectContextFactory;
//
//            @Override
//            public Tree visitNonNull(Tree tree, ExecutionContext executionContext) {
//                return super.visitNonNull(tree, executionContext);
//            }
//
//            @Override
//            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
//                return true;
//            }
//
//            @Override
//            public void visit(@Nullable List<? extends Tree> nodes, ExecutionContext executionContext) {
//
//                super.visit(nodes, executionContext);
//
//                // create the required classes
//                initBeans(executionContext);
//
//                // transform nodes to SourceFiles
//                List<SourceFile> sourceFiles = nodes.stream().filter(SourceFile.class::isInstance).map(SourceFile.class::cast).toList();
//
//                // FIXME: base dir calculation is fake
//                Path baseDir = executionContext.getMessage("base.dir");
//
//                // Create the SBM resource set abstraction
//                ProjectResourceSet projectResourceSet = projectResourceSetFactory.create(baseDir, sourceFiles);
//                // Create the SBM ProjectContext
//                ProjectContext pc = projectContextFactory.createProjectContext(baseDir, projectResourceSet);
//
//                // Execute the SBM Action = the JAXRS Recipe
//                new ConvertJaxRsAnnotations().apply(pc);
//
//                // Merge back result
//                List<? extends Tree> modifiedNodes = merge(nodes, pc.getProjectResources());
//
//                // Process other
//                super.visit(modifiedNodes, executionContext);
//            }
//
//        };
//    }

    private List<? extends SourceFile> merge(List<SourceFile> nodes, ProjectResourceSet projectResources) {
        // merge the changed results into the given list and return the result
        ArrayList<SourceFile> merged = new ArrayList<>();
        merged.addAll(nodes);
        projectResources.stream()
                .filter(r -> r.hasChanges())
                .forEach(changed -> {
                    int pos = findPosition(merged, changed);
                    merged.add(pos, changed.getSourceFile());
                });
        return merged;
    }

    private int findPosition(List<SourceFile> merged, RewriteSourceFileHolder<? extends SourceFile> changed) {
        List<String> paths = merged.stream().map(f -> f.getSourcePath().toString()).toList();
        return paths.indexOf(changed.getSourcePath().toString());
    }


    private void initBeans(ExecutionContext executionContext) {
        RewriteSourceFileWrapper sourceFileWrapper = new RewriteSourceFileWrapper();
        SbmApplicationProperties sbmApplicationProperties = new SbmApplicationProperties();
        JavaParserBuilder parserBuilder = new JavaParserBuilder();
        List<ProjectResourceWrapper> projectResourceWrappers = new ArrayList<>();
        RewriteMigrationResultMerger merger = new RewriteMigrationResultMerger(sourceFileWrapper);
        ProjectResourceSetHolder holder = new ProjectResourceSetHolder(executionContext, merger);
        JavaRefactoringFactory refactoringFactory = new JavaRefactoringFactoryImpl(holder, executionContext);
        projectResourceWrappers.add(new JavaSourceProjectResourceWrapper(refactoringFactory, parserBuilder, executionContext));


        projectResourceSetFactory = new ProjectResourceSetFactory(new RewriteMigrationResultMerger(sourceFileWrapper), sourceFileWrapper, executionContext);
        ProjectResourceWrapperRegistry registry = new ProjectResourceWrapperRegistry(projectResourceWrappers);
        BasePackageCalculator calculator = new BasePackageCalculator(sbmApplicationProperties);

        ProjectResourceSetFactory resourceSetFactory = new ProjectResourceSetFactory(merger, sourceFileWrapper, executionContext);

        projectContextFactory = new ProjectContextFactory(registry, holder, refactoringFactory, calculator, parserBuilder, executionContext, merger, resourceSetFactory);
    }

}
