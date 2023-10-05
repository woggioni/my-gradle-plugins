package net.woggioni.gradle.sambal.attribute;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.ReusableAction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public interface Signing extends Named {
    Attribute<Signing> SIGNING_ATTRIBUTE = Attribute.of("net.woggioni.gradle.lys.artifact.signing", Signing.class);

    String signed = "signed";
    String unsigned = "unsigned";

    class CompatibilityRules implements AttributeCompatibilityRule<Signing>, ReusableAction {
        public void execute(CompatibilityCheckDetails<Signing> details) {
            Signing consumerValue = details.getConsumerValue();
            Signing producerValue = details.getProducerValue();
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

    class DisambiguationRules implements AttributeDisambiguationRule<Signing>, ReusableAction {
        private static final List<String> ORDER = Arrays.asList(unsigned, signed);
        private static final Comparator<Signing> comparator =
                Comparator.comparingInt(signing -> ORDER.indexOf(signing.getName()));

        @Override
        public void execute(MultipleCandidatesDetails<Signing> details) {
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
