package com.company.erp.accounting;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private final com.tngtech.archunit.core.domain.JavaClasses classes = new ClassFileImporter().importPackages("com.company.erp.accounting");
    @Test void domainIsFrameworkFree() { noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..application..", "..adapter..", "..config..").check(classes); }
    @Test void applicationDoesNotDependOnAdapters() { noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("..adapter..").check(classes); }
    @Test void inboundDoesNotReachPersistence() { noClasses().that().resideInAPackage("..adapter.in..").should().dependOnClassesThat().resideInAPackage("..adapter.out..").check(classes); }
}
