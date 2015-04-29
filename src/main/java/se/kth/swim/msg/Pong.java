package se.kth.swim.msg;

import java.util.List;
import java.util.Set;

import se.kth.swim.ViewNode;

public class Pong {
	private final List<ViewNode> view;
	
	public Pong(List<ViewNode> view) {
		this.view = view;
	}
	
	public List<ViewNode> getView() {
		return view;
	}
}
