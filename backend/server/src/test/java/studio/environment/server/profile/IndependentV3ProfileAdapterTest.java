package studio.environment.server.profile;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import studio.environment.core.profile.ProfileComposer;
import studio.environment.core.profile.V3ProfileComposer;
import studio.environment.server.definition.BoundedDocumentParser;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;

class IndependentV3ProfileAdapterTest {
    private final V3ProfileBytesAdapter adapter = new V3ProfileBytesAdapter();

    @Test void missingAndExtraInventoryCannotBeCapturedAsACompletePortableProfile() throws Exception {
        var definition = V3ProfileBytesAdapterTest.definition();
        var pin = V3ProfileBytesAdapterTest.pin(definition);
        var original = V3ProfileBytesAdapterTest.snapshot(pin, V3ProfileBytesAdapterTest.GLYPHS);
        for (var documents : List.of(original.documents().subList(0, 1),
                List.of(original.documents().get(0), original.documents().get(1), original.documents().get(1)))) {
            var snapshot = new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(), pin.logicalDigest(),
                    pin.bindingId(), pin.bindingDigest(), documents);
            assertInstanceOf(V3ProfileBytesAdapter.Result.Rejected.class,
                    adapter.capture(definition, pin, snapshot, V3ProfileBytesAdapterTest.command(), () -> false));
        }
    }

    @Test void composeRequiresExactCurrentBytesAndHonorsCancellation() throws Exception {
        var definition = V3ProfileBytesAdapterTest.definition();
        var pin = V3ProfileBytesAdapterTest.pin(definition);
        var profile = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                adapter.read(definition, V3ProfileBytesAdapterTest.fixture(), BoundedDocumentParser.Format.JSON));
        var preview = assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,
                adapter.preview(definition, profile, Set.of("first"))).preview();
        var decisions = List.<ProfileComposer.Decision>of(new ProfileComposer.Decision.Create("first", "new-glyph"),
                new ProfileComposer.Decision.Create("dependency", "new-palette"));
        var changed = V3ProfileBytesAdapterTest.snapshot(pin,
                V3ProfileBytesAdapterTest.GLYPHS.replace("donor-tone", "donor-&#116;one"));
        assertEquals("STALE_INPUT", assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                adapter.compose(definition, profile, preview, pin, changed, decisions, () -> false))
                .diagnostics().getFirst().code());
        assertEquals("CANCELLED", assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                adapter.compose(definition, profile, preview, pin,
                        V3ProfileBytesAdapterTest.snapshot(pin, V3ProfileBytesAdapterTest.GLYPHS), decisions, () -> true))
                .diagnostics().getFirst().code());
    }
}
