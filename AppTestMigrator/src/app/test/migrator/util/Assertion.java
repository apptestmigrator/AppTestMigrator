package app.test.migrator.util;

import java.util.List;

public class Assertion {
	String targetElement;
	String assertionMethod;
	List<String> matchers;
	
	public Assertion (String targetElement, String assertionMethod, List<String> matchers) {
		this.targetElement = targetElement;
		this.assertionMethod = assertionMethod;
		this.matchers = matchers;
	}
	
	public String getTargetElement() { return targetElement; }
	public String getAssertionMethod() { return assertionMethod; }
	public List<String> getMatchers() { return matchers; }
}
