package app.test.migrator.util;

public class UINode {
	String id;
	int top;
	int left;
	int right;
	int bottom;
	
	public UINode(String id, int top, int left, int right, int bottom){
		this.id = id;
		this.top = top;
		this.left = left;
		this.right = right;
		this.bottom = bottom;
	}
	
	public String toString(){	return id + " " + top + " " + left + " " + right + " " + bottom;	}
}
