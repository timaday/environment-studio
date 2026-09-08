package studio.environment.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    @Test void coreHasNoFrameworkOrExternalIoDependencies() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("studio.environment.core");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "java.sql..", "javax.sql..", "org.w3c..",
                "javax.xml..", "jakarta..", "java.io..", "java.nio.file..", "java.net..")
                .check(classes);
    }
}
