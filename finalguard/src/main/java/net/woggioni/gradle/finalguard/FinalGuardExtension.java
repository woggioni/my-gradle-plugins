package net.woggioni.gradle.finalguard;

import org.gradle.api.provider.Property;

import javax.tools.Diagnostic;


public interface FinalGuardExtension {
    Property<Diagnostic.Kind> getDiagnosticKind();
}
