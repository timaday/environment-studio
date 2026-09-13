package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;

class PrivacyHashTest {
    static Path scratch,probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-native-hash-");probe=scratch.resolve("probe");
        assertEquals(0,PrivacyPeerTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror",
            "-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now",
            "-Wl,--wrap=pread,--wrap=__pread_chk,--wrap=fstat,--wrap=clock_gettime,--wrap=OSSL_LIB_CTX_new,--wrap=OSSL_LIB_CTX_free,--wrap=OSSL_PROVIDER_load,--wrap=OSSL_PROVIDER_unload,--wrap=EVP_MD_fetch,--wrap=EVP_MD_free,--wrap=EVP_MD_CTX_new,--wrap=EVP_MD_CTX_free,--wrap=EVP_DigestInit_ex2,--wrap=EVP_DigestUpdate,--wrap=EVP_DigestFinal_ex",
            "-Isrc/main/c","src/main/c/privacy-hash.c","src/test/c/privacy-hash-probe.c","-lcrypto","-o",probe.toString())));
    }
    @Test void actualOwnedFileMatchesIndependentKnownDigest() throws Exception {
        assertEquals(0,PrivacyPeerTest.run(List.of(probe.toString(),"live")));
    }
    void check(String... modes) throws Exception {for(String mode:modes)assertEquals(0,PrivacyPeerTest.run(List.of(probe.toString(),mode)),mode);}
    @Test void independentEmptyAndMillionByteVectors() throws Exception {check("empty","million");}
    @Test void partialAndInterruptedReadsDoNotChangeTheDigest() throws Exception {check("partial","eintr");}
    @Test void exactPerFileAndWholeLaunchByteLimits() throws Exception {check("large","too-large","max-total");}
    @Test void exactOccurrenceLimitIncludesRepeatedEmptyFiles() throws Exception {check("max-objects");}
    @Test void invalidArgumentsPreserveOwnerAndWipeDistinctOutput() throws Exception {check("null-owner","null-output","overlap","negative-fd","control-alias");}
    @Test void unavailableOrIneligibleDescriptorsRefuse() throws Exception {check("directory","wrong-mode","no-cloexec","stat-fault","read-fault","final-stat-fault");}
    @Test void actualFileMutationRefuses() throws Exception {check("grow","truncate","change-content");}
    @Test void everyComparedMetadataFieldIsRequired() throws Exception {check("device","inode","size","mode","uid","gid","mtime","ctime");}
    @Test void originalControlsApplyFromOpenThroughFinalMetadata() throws Exception {check("expired-open","long-open","cancel-open","cancel-read","final-cancel","final-deadline","cancel-stat","cancel-final");}
    @Test void everyCryptoFailureIsTypedAndPartialOwnersCloseOnce() throws Exception {check("library-fault","provider-fault","algorithm-fault","context-fault","init-fault","update-fault","final-fault","wrong-digest-size");}
    @Test void uncertainProviderReleaseIsStickyAndNeverRetried() throws Exception {check("close-fault","update-close-fault");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
