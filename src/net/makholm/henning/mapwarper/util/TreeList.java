package net.makholm.henning.mapwarper.util;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

public final class TreeList<T> extends AbstractList<T> {

  public static <T> List<T> concat(List<T> a, List<T> b) {
    if( a == null || a.isEmpty() )
      return b;
    else if( b == null || b.isEmpty() )
      return a;
    else
      return new TreeList<>(a, b);
  }

  public static <T> List<T> concat(List<T> a, List<T> b, List<T> c) {
    return concat(a, concat(b, c));
  }

  private final List<T> a, b;
  private final int asize, bsize;

  private TreeList(List<T> a, List<T> b) {
    this.a = a;
    this.b = b;
    this.asize = a.size();
    this.bsize = b.size();
  }

  @Override
  public int size() {
    return asize + bsize;
  }

  @Override
  public T get(int index) {
    if( index < asize )
      return a.get(index);
    else
      return b.get(index-asize);
  }

  @Override
  public Iterator<T> iterator() {
    return new TreeListIterator<T>(this);
  }

  private static class TreeListIterator<U> implements Iterator<U> {
    List<U> current;
    int index;
    final ArrayDeque<List<U>> stack = new ArrayDeque<>();

    TreeListIterator(TreeList<U> creator) {
      stack.addFirst(creator);
      nextIterator();
    }

    @Override
    public boolean hasNext() {
      return current != null;
    }

    @Override
    public U next() {
      U result = current.get(index);
      index++;
      if( index >= current.size() ) {
        index = 0;
        // TODO? We could implement ListIterator<U> if we kept a record
        // of sublists after we've been through them.
        // But who uses ListIterator anyway?
        nextIterator();
      }
      return result;
    }

    public void nextIterator() {
      if( stack.isEmpty() ) {
        current = null;
      } else {
        current = stack.removeFirst();
        while( current instanceof TreeList<U> subtree ) {
          stack.addFirst(subtree.b);
          current = subtree.a;
        }
      }
    }
  }

}
