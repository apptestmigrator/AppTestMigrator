package app.test.migrator.code.generation;

import static app.test.migrator.code.generation.MatcherBuilder.Kind.ClassName;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.util.StringHelper.boxString;

public class MatcherBuilder {
	public enum Kind {Id, Text, ContentDescription, ClassName, XPath}

	private int matcherCount = 0;
	private final StringBuilder matchers = new StringBuilder();

	public MatcherBuilder() {
	}

	public void addMatcher(Kind kind, String matchedString, boolean shouldBox, boolean isAssertionMatcher) {
		if (!isNullOrEmpty(matchedString)) {
			if (kind == ClassName && !isAssertionMatcher) {
				matchedString = getInternalName(matchedString);
			}

			if (matcherCount > 0) {
				matchers.append(", ");
			}

			if (kind == ClassName && isAssertionMatcher) {
				matchers.append("IsInstanceOf.<View>instanceOf(" + matchedString + ".class)");
			} else {
				matchers.append("with").append(kind.name()).append(kind == ClassName ? "(is(" : "(")
				.append(shouldBox ? boxString(matchedString) : matchedString).append(kind == ClassName ? "))" : ")");
			}

			matcherCount++;
		}
	}

	/**
	 * Returns the name of the class that can be used in the generated test code.
	 * For example, for a class foo.bar.Foo.Bar it returns foo.bar.Foo$Bar.
	 */
	private String getInternalName(String className) {
		// If the PsiClass was not found or its internal name was not obtained, apply a simple heuristic.
		String[] nameFragments = className.split("\\.");
		String resultClassName = "";
		for (int i = 0; i < nameFragments.length - 1; i++) {
			String fragment = nameFragments[i];
			resultClassName += fragment + (Character.isUpperCase(fragment.charAt(0)) ? "$" : ".");
		}
		resultClassName += nameFragments[nameFragments.length -1];

		return resultClassName;
	}

	public int getMatcherCount() {
		return matcherCount;
	}

	public String getMatchers() {
		return matchers.toString();
	}

}
