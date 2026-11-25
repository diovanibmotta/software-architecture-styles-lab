package com.github.diovanibmotta.layered;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** The style's core constraint, executable: closed layers, requests only flow downwards. */
@AnalyzeClasses(packages = "com.github.diovanibmotta.layered", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layersAreClosed = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Presentation").definedBy("..presentation..")
            .layer("Business").definedBy("..business..")
            .layer("Persistence").definedBy("..persistence..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Business").mayOnlyBeAccessedByLayers("Presentation")
            // closed layer: presentation may NOT skip business and query persistence directly
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Business");

    @ArchTest
    static final ArchRule domainDependsOnNoLayer = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..presentation..", "..business..", "..persistence..");

    @ArchTest
    static final ArchRule onlyPersistenceTalksToTheDatabase = noClasses()
            .that().resideOutsideOfPackage("..persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..");

    @ArchTest
    static final ArchRule onlyPresentationKnowsHttp = noClasses()
            .that().resideOutsideOfPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..");
}
