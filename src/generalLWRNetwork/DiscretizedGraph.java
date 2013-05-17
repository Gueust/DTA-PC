package generalLWRNetwork;

import generalNetwork.graph.Graph;
import generalNetwork.graph.Link;
import generalNetwork.graph.Node;
import generalNetwork.graph.Path;
import generalNetwork.state.splitRatios.IntertemporalSplitRatios;

import java.util.Iterator;
import java.util.LinkedList;

public class DiscretizedGraph {

  /*
   * Contains the junctions which were not present in the graph added
   * because of the discretization
   */
  LinkedList<Junction> new_junctions;
  /* Contains all the cells created */
  LinkedList<Cell> new_cells;
  /*
   * link_to_cells[i] contains the head and tail cell of the discretized version
   * of a given link
   */
  LinkPair[] link_to_cells;
  /* junctions[i] contains the junctions representing nodes[i] */
  Junction[] junctions;
  /* Contains the sources */
  Origin[] sources;
  /* Contains the destinations */
  Destination[] destinations;
  int total_nb_junctions = 0, total_nb_cells = 0;
  

  IntertemporalSplitRatios split_ratios;
  int nb_paths;
  
  public DiscretizedGraph(Graph g, double delta_t, int time_steps) {
    new_cells = new LinkedList<Cell>();
    new_junctions = new LinkedList<Junction>();

    /* Reset the unique id generators for cells and junctions */
    NetworkUIDFactory.resetCell_id();
    NetworkUIDFactory.resetJunction_id();

    /* Discretize all the links */
    Link[] links = g.getLinks();
    link_to_cells = new LinkPair[links.length];
    for (int i = 0; i < links.length; i++) {
      link_to_cells[i] = discretizeLink(links[i], delta_t);
    }

    /* Transform Nodes into Junctions */
    Node[] nodes = g.getNodes();
    junctions = new Junction[nodes.length];
    for (int i = 0; i < nodes.length; i++) {
      junctions[i] = discretizeJunction(nodes[i], delta_t);
    }

    /*
     * We transform each origin node into an origin junction of the
     * corresponding type
     */
    int nb_origins = g.getOrigins().length;
    sources = new Origin[nb_origins];
    for (int o = 0; o < nb_origins; o++) {
      sources[o] = new Origin(junctions[g.getOrigins()[o].id],
          g.getOrigins()[o].type,
          new_cells, new_junctions);
    }

    /*
     * We transform every destination node into a destination junction of the
     * corresponding type
     */
    int nb_destinations = g.getDestinations().length;
    destinations = new Destination[nb_destinations];
    for (int d = 0; d < nb_destinations; d++) {
      destinations[d] = new Destination(junctions[g.getDestinations()[d].id],
          g.getDestinations()[d].type, new_cells, new_junctions);
    }

    /*
     * We add the non-compliant split_ratios and the split ratios corresponding
     * to the path of some commodities
     */
    split_ratios = new IntertemporalSplitRatios(junctions, time_steps);

    Path[] paths = g.getPaths();
    for (int i = 0; i < paths.length; i++) {
      createSplitRatios(g, paths[i]);
    }
    nb_paths = paths.length;

    total_nb_junctions = NetworkUIDFactory.IdJunction() + 1;
    total_nb_cells = NetworkUIDFactory.IdCell() + 1;
  }

  /**
   * @brief Take a link and returns the (head, tail) cells of the discretized
   *        link
   * @details It add 1x1 junctions between the cells and all those junctions are
   *          added in new_junctions
   */
  private LinkPair discretizeLink(Link link, double delta_t) {
    LinkPair result = new LinkPair();

    double length = link.l;
    double v = link.v;
    double w = link.w;
    double jam_density = link.jam_density;
    double F_max = link.F_max;

    int nb_cell_to_build = (int) Math.ceil(length / (v * delta_t));
    assert nb_cell_to_build > 0 : "We must build a > 0 number of cells";

    // We build the following links
    Cell cell;
    Junction current_j, previous_j = null;
    int i;
    for (i = 0; i < nb_cell_to_build - 1; i++) {
      cell = new RoadChunk(v * delta_t, v, w, F_max, jam_density);
      new_cells.add(cell);
      if (i == 0) {
        result.begin = cell;
      }
      if (previous_j != null) {
        previous_j.setNext(new Cell[] { cell });
      }
      current_j = new Junction();
      new_junctions.add(current_j);

      current_j.setPrev(new Cell[] { cell });
      previous_j = current_j;
    }

    // Last cell (or unique cell) of the link
    cell = new RoadChunk(v * delta_t, v, w, F_max, jam_density);
    new_cells.add(cell);
    if (i == 0) {
      result.begin = cell;
    }
    result.end = cell;
    if (previous_j != null)
      previous_j.setNext(new Cell[] { cell });

    return result;
  }

  /**
   * @brief Take a node and returns the equivalent junction
   * @details It need link_to_cell to have been initialized
   * @return
   */
  private Junction discretizeJunction(Node node, double delta_t) {
    Junction result = new Junction();

    int nb_incoming = node.incoming_links.size();
    int nb_outgoing = node.outgoing_links.size();
    Cell[] incoming = new Cell[nb_incoming];
    Cell[] outgoing = new Cell[nb_outgoing];
    for (int i = 0; i < nb_incoming; i++) {
      incoming[i] = link_to_cells[i].end;
    }
    for (int i = 0; i < nb_outgoing; i++) {
      outgoing[i] = link_to_cells[i].begin;
    }

    if (nb_incoming != 0)
      result.setPrev(incoming);
    if (nb_outgoing != 0)
      result.setNext(outgoing);

    return result;
  }

  /**
   * @brief Build the split ratios for the compliant and non compliant
   *        commodities
   */
  private void createSplitRatios(Graph g, Path p) {

    Iterator<Integer> iterator = p.iterator();
    Link[] links = g.getLinks();
    Junction j;
    int previous_link_id = -1, current_link_id = -1;
    while (iterator.hasNext()) {
      current_link_id = iterator.next();

      j = junctions[links[current_link_id].from.getUnique_id()];

      // There is nothing to do for 1xn junctions
      if (!j.isMergingJunction()) {
        // We add a split ratio for the first junction (origin)
        // We have to take the id of the buffer placed before the junction
        if (previous_link_id == -1) {
          assert j.getPrev().length == 1 : "A multiple exit origin must have an incoming link";
          split_ratios.addCompliantSRToJunction(
              j.getPrev()[0].getUniqueId(),
              link_to_cells[current_link_id].begin.getUniqueId(),
              p.getUnique_id() + 1, 1, j);
        } else {
          split_ratios.addCompliantSRToJunction(
              link_to_cells[previous_link_id].end.getUniqueId(),
              link_to_cells[current_link_id].begin.getUniqueId(),
              p.getUnique_id() + 1, 1, j);
        }
      }

      previous_link_id = current_link_id;

    }
    /*
     * There is nothing to do for the last node. However we check that the last
     * node is Nx1 junction
     */
    assert (junctions[links[current_link_id].to.getUnique_id()].getNext().length <= 1) : "The arrival of a path should not have multiple exits";
  }
}