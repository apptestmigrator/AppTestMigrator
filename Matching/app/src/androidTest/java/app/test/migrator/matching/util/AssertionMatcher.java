package app.test.migrator.matching.util;

import app.test.migrator.matching.util.uiautomator.UiNode;

public class AssertionMatcher {
    String hierarchyMethod;
    String method;
    UiNode parameter;

    public AssertionMatcher(String hierarchyMethod, String method, UiNode parameter) {
        this.hierarchyMethod = hierarchyMethod;
        this.method = method;
        this.parameter = parameter;
    }

    public String getHierarchyMethod() { return hierarchyMethod; }
    public String getMethod() { return method; }
    public UiNode getParameter() { return parameter; }
}