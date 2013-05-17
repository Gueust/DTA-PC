package generalNetwork.graph;

import com.google.gson.annotations.Expose;

import generalNetwork.graph.Destination;
import generalNetwork.graph.Link;
import generalNetwork.graph.Node;
import generalNetwork.graph.Origin;
import generalNetwork.graph.json.JsonFactory;

public class Graph {

  private transient GraphUIDFactory id_factory;

  /*
   * Never use this numbers but use the equivalent array.length
   * They can be usefull only in the json format if it cannot be loaded
   * directly (because the file is too big)
   */
  @Expose
  private int nb_nodes;
  @Expose
  private int nb_links;
  @Expose
  private int nb_paths;
  @Expose
  private int nb_origins;
  @Expose
  private int nb_destinations;

  @Expose
  Node[] nodes; /* We suppose nodes[i].unique_id = i */
  @Expose
  Link[] links; /* We suppose links[i].unique_id = i */

  @Expose
  Path[] paths;
  @Expose
  Origin[] origins;
  @Expose
  Destination[] destinations;

  static public Graph fromFile(String file_name) {
    return new JsonFactory().fromFile(file_name);
  }

  /**
   * @brief Create an extendible representation of a graph from a fixed Graph
   * @details This is not a deep copy of the graph
   * @param mg
   */
  public Graph(MutableGraph mg) {
    nb_nodes = mg.nodes.size();
    nodes = mg.nodes.toArray(new Node[nb_nodes]);
    nb_links = mg.links.size();
    links = mg.links.toArray(new Link[nb_links]);
    nb_paths = mg.paths.size();
    paths = mg.paths.toArray(new Path[nb_paths]);
    nb_origins = mg.origins.size();
    origins = mg.origins.toArray(new Origin[nb_origins]);
    nb_destinations = mg.destinations.size();
    destinations = mg.destinations.toArray(new Destination[nb_destinations]);

    id_factory = new GraphUIDFactory(nb_links, nb_nodes, nb_paths);
  }

  public void check() {
    for (int i = 0; i < nodes.length; i++)
      assert nodes[i].unique_id == i : "links[i] should have id i";

    for (int i = 0; i < links.length; i++)
      assert links[i].unique_id == i : "nodes[i] should have id i";

    for (int i = 0; i < paths.length; i++)
      assert paths[i].unique_id == i : "paths[i] should have id i";
  }

  /**
   * @brief Has to be used before exporting in Json format
   */
  public void buildBeforeJsonExport() {

    for (int i = 0; i < nodes.length; i++) {
      nodes[i].buildToJson();
    }

    for (int i = 0; i < paths.length; i++)
      paths[i].buildToJson();
  }

  /**
   * @brief Has to be used after importing from Json format
   */
  public void buildAfterJsonExport() {
    for (int i = 0; i < nodes.length; i++) {
      nodes[i].buildFromJson(links);
    }

    for (int i = 0; i < paths.length; i++)
      paths[i].buildFromJson();
  }

  public Link newLink(double l, double v, double w, double f_max,
      double jam_density) {
    return new Link(l, v, w, f_max, jam_density, id_factory);
  }

  public Node newNode() {
    return new Node(id_factory);
  }

  public Path newPath() {
    return new Path(id_factory);
  }

  public void print() {
    System.out.println("Printing the {Link, Node} graph");
    for (int i = 0; i < origins.length; i++) {
      origins[i].print();
    }
  }

  public Node[] getNodes() {
    return nodes;
  }

  public Link[] getLinks() {
    return links;
  }

  public Path[] getPaths() {
    return paths;
  }

  public Origin[] getOrigins() {
    return origins;
  }

  public Destination[] getDestinations() {
    return destinations;
  }
}
