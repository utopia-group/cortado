package edu.utexas.cs.utopia.cortado.util.graph;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import soot.toolkits.graph.DirectedGraph;

@SuppressWarnings("UnstableApiUsage")
public class SootGraphConverter
{
    /**
     * @param sootGraph a soot graph
     * @param <NodeType> type of nodes in the graph
     * @return a copy of the soot graph represented in guava
     */
    public static <NodeType> ImmutableGraph<NodeType> convertToGuavaGraph(DirectedGraph<NodeType> sootGraph)
    {
        final MutableGraph<NodeType> guavaGraph = GraphBuilder.directed()
                .allowsSelfLoops(true)
                .expectedNodeCount(sootGraph.size())
                .build();
        sootGraph.iterator().forEachRemaining(guavaGraph::addNode);
        sootGraph.iterator().forEachRemaining(
                node -> sootGraph.getSuccsOf(node)
                                        .forEach(succ -> guavaGraph.putEdge(node, succ))
        );
        return ImmutableGraph.copyOf(guavaGraph);
    }
}
