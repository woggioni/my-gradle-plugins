package net.woggioni.gradle.osgi.app;

import lombok.Data;

@Data
public class JavaAgent {
    private final String className;
    private final String args;
}
