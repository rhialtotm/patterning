public class Node {
    public Node nw;
    public Node ne;
    public Node sw;
    public Node se;

    public int id;
    public int level;
    public int population;

    public Node cache = null;
    public Node quick_cache = null;
    public Node hashmapNext = null;

    public Node(int id, int population, int level) {
        this.id=id;
        this.population=population;
        this.level=level;
    }

    public Node(Node nw, Node ne, Node sw, Node se, int id) {
        this.nw = nw;
        this.ne = ne;
        this.sw = sw;
        this.se = se;
        this.id = id;

        this.level = nw.level + 1;

        this.population = nw.population + ne.population + sw.population + se.population;
    }
}