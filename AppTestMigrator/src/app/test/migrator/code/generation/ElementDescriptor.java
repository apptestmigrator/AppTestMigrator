package app.test.migrator.code.generation;

import static com.google.common.base.Strings.isNullOrEmpty;

public class ElementDescriptor {
	// Fully qualified class name of the element.
	private final String className;


	// Attribute fields:

	// Position of this element among the children of its parent.
	// The value of -1 signifies that the child position is absent.
	private final int childPosition;
	private final String resourceId;
	private final String contentDescription;
	private final String text;
	private final String xpath;

	public ElementDescriptor(String className, int childPosition, String resourceId, String contentDescription, String text, String xpath) {
		this.className = className;
		this.childPosition = childPosition;
		this.resourceId = resourceId;
		this.contentDescription = contentDescription;
		this.text = text;
		this.xpath = xpath;
	}

	public String getClassName() {
		return className;
	}

	public int getChildPosition() {
		return childPosition;
	}

	public String getResourceId() {
		return resourceId;
	}

	public String getContentDescription() {
		return contentDescription;
	}

	public String getText() {
		return text;
	}

	public String getXPath() {
		return xpath;
	}

	/**
	 * Returns {@code true} iff all attribute fields are absent.
	 */
	public boolean isEmpty() {
		return childPosition == -1 && isEmptyIgnoringChildPosition();
	}

	/**
	 * Returns {@code true} iff all attribute fields not considering {@code childPosition} are absent.
	 */
	public boolean isEmptyIgnoringChildPosition() {
		return isNullOrEmpty(resourceId) && isNullOrEmpty(text) && isNullOrEmpty(contentDescription);
	}
}
