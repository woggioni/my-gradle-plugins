package net.woggioni.gradle.sambal.attribute;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public interface Sealing extends Named {
    Attribute<Sealing> SEALING_ATTRIBUTE = Attribute.of("net.woggioni.gradle.lys.artifact.type", Sealing.class);

    String sealed = "sealed";
    String open = "open";

    class CompatibilityRules implements AttributeCompatibilityRule<Sealing> {
        public void execute(CompatibilityCheckDetails<Sealing> details) {
            Sealing consumerValue = details.getConsumerValue();
            Sealing producerValue = details.getProducerValue();
            if (consumerValue == null) {
                details.compatible();
            } else if(producerValue == null) {
                details.incompatible();
            } else if(Objects.equals(consumerValue.getName(), producerValue.getName())) {
                details.compatible();
            } else {
                details.incompatible();
            }
        }
    }

    class DisambiguationRules implements AttributeDisambiguationRule<Sealing> {
        private static final List<String> ORDER = Arrays.asList(open, sealed);
        private static final Comparator<Sealing> comparator =
                Comparator.comparingInt(sealing -> ORDER.indexOf(sealing.getName()));

        @Override
        public void execute(MultipleCandidatesDetails<Sealing> details) {
            if(details.getConsumerValue() == null) {
                details.closestMatch(null);
            } else {
                details.getCandidateValues().stream()
                        .min(comparator)
                        .ifPresent(details::closestMatch);
            }
        }
    }
}
