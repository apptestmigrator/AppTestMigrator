package app.test.migrator.matching.util;

import app.test.migrator.matching.util.uiautomator.UiNode;

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

    public Event() {
        this.type = null;
        this.targetElement = null;
        this.replacementText = null;
        this.actionCode = null;
    }

    public String getType(){	return type;	}

    public UiNode getTargetElement(){	return targetElement;	}

    public String getReplacementText(){	return replacementText;	}

    public String getActionCode(){	return actionCode;	}

    public void setReplacementText(String replacementText){	this.replacementText = replacementText;	}

    public String toString(){   return type + " " + (targetElement == null ? "" : targetElement.toString()) + " " + replacementText + " " + actionCode;   }
}