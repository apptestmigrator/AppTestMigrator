package app.test.migrator.code.generation;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import app.test.migrator.uiautomator.UiNode;

public class TransformEventsToTestRecorderEvents {	
	private static final int MAX_PARENT_HIERARCHY_EVALUATION_DEPTH = 3;
	private List<Event> events;

	public TransformEventsToTestRecorderEvents(List<Event> events){
		this.events = events;
	}

	public List<Object> getTestRecorderEvents(){
		List<Object> testRecorderEvents = new ArrayList<Object>();

		for(Event event : events){
			TestRecorderEvent testRecorderEvent = new TestRecorderEvent(event.getType());
			if (testRecorderEvent.isPressEvent()) {
				String actionCode = event.getActionCode();
				if (actionCode != null) {
					testRecorderEvent.setActionCode(Integer.parseInt(actionCode));
				}
			}

			UiNode targetElement = event.getTargetElement();
			populateElementDescriptors(testRecorderEvent, targetElement, 1);
			if (testRecorderEvent.getElementDescriptorsCount() > 0) {
				testRecorderEvent.setReplacementText(testRecorderEvent.getElementDescriptor(0).getText());
			}
			testRecorderEvent.setCanScrollTo(Boolean.parseBoolean(targetElement.getAttribute("scrollable")));
			testRecorderEvent.setReplacementText(event.getReplacementText());
			testRecorderEvents.add(testRecorderEvent);
		}

		return testRecorderEvents;	
	}

	@NotNull
	private String getReceiverReference(Event event) {
		String eventType = event.getType();
		if (eventType.equals(TestRecorderEvent.VIEW_CLICK) || eventType.equals(TestRecorderEvent.TEXT_CHANGE)) {
			return "this.this$0";
		} else if (eventType.equals(TestRecorderEvent.LIST_ITEM_CLICK)) {
			return "view";
		}

		return "this";
	}

	private void populateElementDescriptors(TestRecorderEvent testRecorderEvent, UiNode event, int level) {

		if (event == null  || level > MAX_PARENT_HIERARCHY_EVALUATION_DEPTH) {
			return;
		}

		UiNode parentNode = (UiNode)event.getParent();

		// Perform the check only for clicks on the inner most (i.e., first in the hierarchy) element.
		if (parentNode != null && testRecorderEvent.isViewClick() && level == 1) {
			String parentElementType = parentNode.getAttribute("class");
			if (parentElementType != null && "android.support.v7.widget.RecyclerView".equals(parentElementType)) {
				int positionIndex = -1;
				for(int i = 0; i < parentNode.getChildren().length; i++){
					if(((UiNode)(parentNode.getChildren()[i])).toString().equals(event.toString())){
						positionIndex = i;
					}
				}

				if (positionIndex != -1) {
					testRecorderEvent.setRecyclerViewPosition(positionIndex);
				}
			}
		}

		if (!evaluateAndAddElementDescriptor(testRecorderEvent, event, false) && level == 1) {

			String className = event.getAttribute("class");

			// An empty element descriptor for the non-identifiable element.
			testRecorderEvent.addElementDescriptor(new ElementDescriptor(className == null ? "" : className, -1, "", "", "", ""));

			// In case there is no text-identifiable child, use the parent node as the means of identification.
			populateElementDescriptors(testRecorderEvent, parentNode, level + 1);
		}
	}

	private boolean evaluateAndAddElementDescriptor(TestRecorderEvent testRecorderEvent, UiNode targetElement, boolean isTextMandatory) {

		String text = targetElement.getAttribute("text");

		if (isTextMandatory && (text == null || text.equals(""))) {
			return false;
		}

		UiNode parent = (UiNode)targetElement.getParent();

		int childPosition = -1;
		if(parent != null){
			for(int i = 0; i < parent.getChildren().length; i++){
				if(((UiNode)(targetElement.getParent().getChildren()[i])).toString().equals(targetElement.toString())){
					childPosition = i;
				}
			}
		}

		String resourceId = targetElement.getAttribute("resource-id");

		String contentDescription = targetElement.getAttribute("content-desc");

		StringBuilder xpath = new StringBuilder();
		findXPath(xpath, targetElement);

		if (childPosition != -1 || resourceId != null || contentDescription != null || text != null || !xpath.toString().equals("")) {
			String className = targetElement.getAttribute("class");

			testRecorderEvent.addElementDescriptor(new ElementDescriptor(className == null ? "" : className,
					childPosition,
					resourceId,
					contentDescription,
					text,
					xpath.toString()));

			return true;
		}

		return false;
	}

	private void findXPath(StringBuilder xpath, UiNode node) {
		UiNode parent = (UiNode)node.getParent();
		if(parent == null)	return;

		int parentChildrenCount = parent.getChildCount();
		if(parentChildrenCount > 1){
			int index = parentChildrenCount - parent.getChildrenList().indexOf(node);			
			String className = node.getAttribute("class");

			xpath.insert(0, "/" + className + "[" + index + "]");
		} else {
			String className = node.getAttribute("class");

			xpath.insert(0, "/" + className);
		}

		findXPath(xpath, parent);
	}

}
