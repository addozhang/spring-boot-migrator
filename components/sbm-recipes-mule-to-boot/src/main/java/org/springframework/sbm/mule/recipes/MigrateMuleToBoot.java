package org.springframework.sbm.mule.recipes;

import org.openrewrite.Recipe;
import org.openrewrite.maven.AddDependency;

import java.util.List;

/**
 * This is the entrance recipe of the MigrateMuleToBoot.
 */
public class MigrateMuleToBoot extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Mulesoft 3.x to SpringBoot";
    }

    @Override
    public String getDescription() {
        return "Migrate Mulesoft 3.x to SpringBoot";
    }


    /**
     * Get the list of recipes
     * 1. add dependencies for Spring Boot
     * 2. remove dependencies for Mule
     * 3. change Mule flows to Spring Boot controllers
     *
     * @return
     */
    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new AddDependency(
                        "com.snowplowanalytics",
                        "schema-ddl_2.13",
                        "0.25.0",
                        null,
                        null,
                        null,
                        "*",
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }


}
