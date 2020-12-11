package io.compgen.ngsutils.support;

import io.compgen.common.Pair;

/**
 * This counter will count events across a position. For example, the number of events across a genome.
 * This is meant to be a sliding window, so that events are only tracked across the window. Once we are
 * past the window, it's expected that head nodes will be removed.
 * 
 * This is implemented as a Linked List for easy traversal.
 * 
 * @author mbreese
 *
 */

public class WindowCounter {

	class Node {
		final int pos;
		int value;
		Node next;
		
		private Node(int pos, Node next) {
			this.pos = pos;
			this.value = 0;
			this.next = next;
		}
		
		private void incr() {
			this.value++;
		}

		public Pair<Integer, Integer> toPair() {
			return new Pair<Integer, Integer>(pos, value);
		}
	}

	private int size = 0;
	Node head = null;
	
	public int size() {
		return size;
	}

	public Pair<Integer, Integer> pop() {
		if (head == null) {
			return null;
		}

		Node tmp = head;
		head = head.next;
		size--;

		return tmp.toPair();
		
	}

	public Pair<Integer,Integer> head() {
		if (head==null) {
			return null;
		}
		return head.toPair();
	}

	public void incr(int pos) {
		if (this.head == null) {
			this.head = new Node(pos, null);
			this.head.incr();
			size++;
			return;
		}
		
		if (pos < this.head.pos) {
			Node tmp = new Node(pos, this.head);
			tmp.incr();
			size++;
			this.head = tmp;
			return;
		}
		
		
		Node cur = this.head;
		while (cur.next != null && cur.next.pos < pos ) {
			cur = cur.next;
		}

		if (cur.pos == pos) {
			cur.incr();
		} else if (cur.next != null && cur.next.pos == pos) {
			cur.next.incr();
		} else {
			Node oldNext = cur.next;
			cur.next = new Node(pos, oldNext);
			size++;
			cur.next.incr();
		}
	}
}
