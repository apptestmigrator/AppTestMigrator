package app.test.migrator.code.generation;

import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.util.StringHelper.getClassName;
import static com.google.gct.testrecorder.util.StringHelper.parseId;

public abstract class ElementAction {

  /**
   * Descriptors of elements starting with the affected one and up the UI hierarchy.
   */
  private final List<ElementDescriptor> elementDescriptors = Lists.newLinkedList();

  /**
   * Whether the element can be scrolled to (e.g., when it is inside a ScrollView).
   */
  private boolean canScrollTo;

  public ElementDescriptor getElementDescriptor(int index) {
    return elementDescriptors.get(index);
  }

  public List<ElementDescriptor> getElementDescriptorList() {
    return ImmutableList.copyOf(elementDescriptors);
  }

  public int getElementDescriptorsCount() {
    return elementDescriptors.size();
  }

  public void addElementDescriptor(ElementDescriptor descriptor) {
    elementDescriptors.add(descriptor);
  }

  public boolean canScrollTo() {
    return canScrollTo;
  }

  public void setCanScrollTo(boolean canScrollTo) {
    this.canScrollTo = canScrollTo;
  }

  /**
   * Returns the string that represents this element action in the recording dialog.
   */
  public String getRendererString() {
    String displayText = getDisplayText();
    if (!displayText.isEmpty()) {
      return getRendererString(displayText);
    }

    String displayContentDescription = getDisplayContentDescription();
    if (!displayContentDescription.isEmpty()) {
      return getRendererString(displayContentDescription);
    }

    String displayResourceId = getDisplayResourceId();
    if (!displayResourceId.isEmpty()) {
      return getRendererString(displayResourceId);
    }

    int childPosition = getElementChildPosition();
    if (childPosition != -1) {
      return getRendererString(getIdAttributeDisplayPresentation("child position", String.valueOf(childPosition)));
    }

    String className = getElementClassName();
    if (!isNullOrEmpty(className)) {
      return getClassName(className);
    }

    return "unidentified element";
  }

  protected String getDisplayResourceId() {
    String resourceId = getElementResourceId();
    if (!resourceId.isEmpty()) {
      Pair<String, String> parsedId = parseId(resourceId);
      return getIdAttributeDisplayPresentation("ID", parsedId == null ? resourceId : parsedId.getSecond());
    }
    return "";
  }

  protected String getDisplayText() {
    String text = getElementText();
    if (!text.isEmpty()) {
      return getIdAttributeDisplayPresentation("text", text);
    }
    return "";
  }

  protected String getDisplayContentDescription() {
    String contentDescription = getElementContentDescription();
    if (!contentDescription.isEmpty()) {
      return getIdAttributeDisplayPresentation("content description", contentDescription);
    }
    return "";
  }

  protected String getIdAttributeDisplayPresentation(String idAttributeKind, String idAttributeValue) {
    final String idTextColor = /*isUnderDarcula() ? "#eeeeee" :*/ "#111111";
    return idAttributeKind + " <span style='color: " + idTextColor + "; font-weight: bold;'>" + idAttributeValue + "</span>";
  }

  @NotNull
  protected String getRendererString(String displayElementAttribute) {
    String elementClassName = getElementClassName();
    String displayElementType = isNullOrEmpty(elementClassName) ? "element" : getClassName(elementClassName);
    return displayElementType + " with " + displayElementAttribute;
  }

  /**
   * Returns top-level element class name, if present. Otherwise, returns an empty string.
   */
  public String getElementClassName() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getClassName();
    }
    return "";
  }

  /**
   * Returns top-level element child position.
   */
  public int getElementChildPosition() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getChildPosition();
    }
    return -1;
  }

  /**
   * Returns top-level element resource id, if present. Otherwise, returns an empty string.
   */
  public String getElementResourceId() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getResourceId();
    }
    return "";
  }

  /**
   * Returns top-level element text, if present. Otherwise, returns an empty string.
   */
  public String getElementText() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getText();
    }
    return "";
  }

  /**
   * Returns top-level element content description, if present. Otherwise, returns an empty string.
   */
  public String getElementContentDescription() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getContentDescription();
    }
    return "";
  }
}
