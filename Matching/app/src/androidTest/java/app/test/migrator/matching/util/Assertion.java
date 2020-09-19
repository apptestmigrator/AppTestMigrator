package app.test.migrator.matching.util;

import java.util.List;

import app.test.migrator.matching.util.uiautomator.UiNode;

public class Assertion {
    UiNode targetElement;
    String assertionMethod;
    List<AssertionMatcher> matchers;
    boolean visited;

    public Assertion (UiNode targetElement, String assertionMethod, List<AssertionMatcher> matchers) {
        this.targetElement = targetElement;
        this.assertionMethod = assertionMethod;
        this.matchers = matchers;
        this.visited = false;
    }

    public UiNode getTargetElement() { return targetElement; }
    public String getAssertionMethod() { return assertionMethod; }
    public List<AssertionMatcher> getMatchers() { return matchers; }
    public void setVisited() {  this.visited =true; }
    public boolean getVisited() {  return visited; }
}