package net.woggioni.gradle.finalguard;

import org.gradle.api.provider.Property;

import javax.tools.Diagnostic;


public interface FinalGuardExtension {
    Property<Diagnostic.Kind> getDefaultLevel();

    Property<Diagnostic.Kind> getLocalVariableLevel();

    Property<Diagnostic.Kind> getLambdaParameterLevel();

    Property<Diagnostic.Kind> getForLoopParameterLevel();

    Property<Diagnostic.Kind> getTryWithResourceLevel();

    Property<Diagnostic.Kind> getMethodParameterLevel();

    Property<Diagnostic.Kind> getAbstractMethodParameterLevel();

    Property<Diagnostic.Kind> getCatchParameterLevel();
}
