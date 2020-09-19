package app.test.migrator.code.generation;

import app.test.migrator.uiautomator.UiNode;

public class Event {
	String type;
	UiNode targetElement;
	String replacementText;
	String actionCode;
	
	public Event(String type, UiNode targetElement, String replacementText, String actionCode){
		this.type = type;
		this.targetElement = targetElement;
		this.replacementText = replacementText;
		this.actionCode = actionCode;
	}
	
	public String getType(){	return type;	}
	
	public UiNode getTargetElement(){	return targetElement;	}
	
	public String getReplacementText(){	return replacementText;	}
	
	public String getActionCode(){	return actionCode;	}
	
}
