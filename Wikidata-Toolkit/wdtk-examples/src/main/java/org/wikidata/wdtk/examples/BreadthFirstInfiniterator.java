package org.wikidata.wdtk.examples;

import org.jgrapht.Graph;
import org.jgrapht.traverse.CrossComponentIterator;

import java.util.ArrayDeque;
import java.util.Deque;

public class BreadthFirstInfiniterator<V, E>
        extends CrossComponentIterator<V, E, Object>
{
    private Deque<V> queue = new ArrayDeque<>();

    /**
     * Creates a new breadth-first iterator for the specified graph.
     *
     * @param g the graph to be iterated.
     */
    public BreadthFirstInfiniterator(Graph<V, E> g)
    {
        this(g, (V) null);
    }

    /**
     * Creates a new breadth-first iterator for the specified graph. Iteration will start at the
     * specified start vertex and will be limited to the connected component that includes that
     * vertex. If the specified start vertex is <code>null</code>, iteration will start at an
     * arbitrary vertex and will not be limited, that is, will be able to traverse all the graph.
     *
     * @param g the graph to be iterated.
     * @param startVertex the vertex iteration to be started.
     */
    public BreadthFirstInfiniterator(Graph<V, E> g, V startVertex)
    {
        super(g, startVertex);
    }

//    /**
//     * Creates a new breadth-first iterator for the specified graph. Iteration will start at the
//     * specified start vertices and will be limited to the connected component that includes those
//     * vertices. If the specified start vertices is <code>null</code>, iteration will start at an
//     * arbitrary vertex and will not be limited, that is, will be able to traverse all the graph.
//     *
//     * @param g the graph to be iterated.
//     * @param startVertices the vertices iteration to be started.
//     */
//    public BreadthFirstInfiniterator(Graph<V, E> g, Iterable<V> startVertices)
//    {
//        super(g, startVertices);
//    }

    /**
     * @see CrossComponentIterator#isConnectedComponentExhausted()
     */
    @Override
    protected boolean isConnectedComponentExhausted()
    {
        return false;
    }

    /**
     * @see CrossComponentIterator#encounterVertex(Object, Object)
     */
    @Override
    protected void encounterVertex(V vertex, E edge)
    {
        //putSeenData(vertex, null);
        queue.add(vertex);
    }

    /**
     * @see CrossComponentIterator#encounterVertexAgain(Object, Object)
     */
    @Override
    protected void encounterVertexAgain(V vertex, E edge)
    {
        queue.add(vertex);
    }

    /**
     * @see CrossComponentIterator#provideNextVertex()
     */
    @Override
    protected V provideNextVertex()
    {
        return queue.removeFirst();
    }
}
