package app.test.migrator.util;

public class Event {
	String action;
	UINode targetElement;
	
	public Event(String action, UINode targetElement){
		this.action = action;
		this.targetElement = targetElement;
	}
	
	public String getAction(){	return action;	}
	
	public UINode getTargetElement(){	return targetElement;	}
	
	public String toString(){	return action + " " + targetElement.toString();	}
	
}
