package studio.environment.core.profile;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class ProfileArchitectureTest {
    @Test void profileDomainDoesNotExposeWireByteSerialization() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("studio.environment.core.profile");
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("studio.environment.core.profile")
                .and().arePublic().should().haveRawReturnType(byte[].class).check(classes);
    }
}
