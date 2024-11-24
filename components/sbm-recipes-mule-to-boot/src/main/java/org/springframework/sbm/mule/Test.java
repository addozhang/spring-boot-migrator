package org.springframework.sbm.mule;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.sbm.parsers.RewriteMavenProjectParser;
import org.springframework.sbm.parsers.RewriteProjectParsingResult;
import org.springframework.sbm.recipes.RewriteRecipeDiscovery;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@ComponentScan("org.springframework.sbm")
public class Test implements ApplicationRunner {
    public static void main(String[] args) {
        SpringApplication.run(Test.class);
    }

    @Autowired
    private RewriteRecipeDiscovery recipeDiscovery;
    @Autowired
    private RewriteMavenProjectParser mavenProjectParser;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Recipe> recipes = recipeDiscovery.discoverRecipes();
        Optional<Recipe> migrateMuleToBoot = recipes.stream().filter(recipe -> recipe.getName().equals("org.springframework.sbm.mule.recipes.MigrateMuleToBoot")).findFirst();

        if (!migrateMuleToBoot.isPresent()) {
            return;
        }

        RewriteProjectParsingResult parsedResult = mavenProjectParser.parse(Path.of("/Users/addo/Workspaces/private_w/rewrite-demo"));
        System.out.println(parsedResult.sourceFiles().size());
        RecipeRun run = migrateMuleToBoot.get().run(new InMemoryLargeSourceSet(parsedResult.sourceFiles()), new InMemoryExecutionContext());

        System.out.println(run.getChangeset().getAllResults().size());
        System.out.println(Arrays.toString(run.getChangeset().getAllResults().toArray()));
    }
}
