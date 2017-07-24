package org.wikidata.wdtk.examples;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.JApplet;

import org.jgraph.JGraph;
import org.jgraph.graph.*;

import org.jgrapht.ListenableGraph;
import org.jgrapht.ext.JGraphModelAdapter;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.json.JSONObject;

/**
 * A demo applet that shows how to use JGraph to visualize JGraphT graphs.
 *
 * @author Barak Naveh
 *
 * @since Aug 3, 2003
 */
public class WikiArtistVisualizer extends JApplet {
    private static final Color     DEFAULT_BG_COLOR = Color.decode( "#FAFBFF" );
    private static final Dimension DEFAULT_SIZE = new Dimension( 2000, 1000 );

    //
    private JGraphModelAdapter m_jgAdapter;

    /**
     * @see java.applet.Applet#init().
     */
    public void init(  ) {

        WikiArtistNetwork wiki = new WikiArtistNetwork();

        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-final-v1.json").getPath());

        JSONObject wikiJson = null;
        try {
            wikiJson = new JSONObject(new Scanner(file).useDelimiter("\\Z").next());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        wiki.buildGraph(wikiJson);

        // create a JGraphT graph
        //ListenableGraph g = new ListenableDirectedGraph( DefaultEdge.class );

        // create a visualization using JGraph, via an adapter
        m_jgAdapter = new JGraphModelAdapter( wiki.graph );

        JGraph jgraph = new JGraph( m_jgAdapter );

        adjustDisplaySettings( jgraph );
        getContentPane(  ).add( jgraph );
        resize( DEFAULT_SIZE );

        System.out.println("Positioning vertices");

//        Random random = new Random();
//
//        for (String vertex : wiki.graph.vertexSet()) {
//            int x = random.nextInt(1000);
//            int y = random.nextInt(1000);
//            positionVertexAt(vertex, x, y);
//            System.out.print('.');
//        }

        HashSet<Point2D.Double> unique = new HashSet<>();
        Random r = new Random();
        GraphLayoutCache cache = jgraph.getGraphLayoutCache();
        for (Object item : jgraph.getRoots()) {
            GraphCell cell = (GraphCell) item;
            CellView view = cache.getMapping(cell, true);
            Rectangle2D bounds = view.getBounds();
            int currentSize = unique.size();
            double x = 0.0;
            double y = 0.0;
            while (unique.size() == currentSize) {
                x = r.nextInt(10000);
                y = r.nextInt(10000);
                unique.add(new Point2D.Double(x,y));
            }
            bounds.setRect(x, y, bounds.getWidth(), bounds.getHeight());
        }


        // position vertices nicely within JGraph component
//        positionVertexAt( "v1", 130, 40 );
//        positionVertexAt( "v2", 60, 200 );
//        positionVertexAt( "v3", 310, 230 );
//        positionVertexAt( "v4", 380, 70 );

        // that's all there is to it!...
    }


    private void adjustDisplaySettings( JGraph jg ) {
        jg.setPreferredSize( DEFAULT_SIZE );

        Color  c        = DEFAULT_BG_COLOR;
        String colorStr = null;

        try {
            colorStr = getParameter( "bgcolor" );
        }
        catch( Exception e ) {}

        if( colorStr != null ) {
            c = Color.decode( colorStr );
        }

        jg.setBackground( c );
    }


    private void positionVertexAt( Object vertex, int x, int y ) {
        DefaultGraphCell cell = m_jgAdapter.getVertexCell( vertex );
        Map              attr = cell.getAttributes(  );
        Rectangle2D b    = GraphConstants.getBounds( attr );

        GraphConstants.setBounds( attr, new Rectangle( x, y, b.getBounds().width, b.getBounds().height ) );

        Map cellAttr = new HashMap(  );
        cellAttr.put( cell, attr );
        m_jgAdapter.edit(cellAttr, null, null, null);
    }
}