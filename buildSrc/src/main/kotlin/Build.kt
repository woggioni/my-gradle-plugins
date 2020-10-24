import org.gradle.api.Project

operator fun Project.get(key : String) : String? {
    return property(key) as String?
}