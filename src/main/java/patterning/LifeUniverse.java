package patterning;

import java.math.BigInteger;
import java.nio.IntBuffer;
import java.util.HashMap;
/*

 great article:  https://www.dev-mind.blog/hashlife/ and code: https://github.com/ngmsoftware/hashlife


* this wikipedia article talks about hash caching, superspeed memoization and representation of the tree as well as manual garbage collection
- better than chatgpt - refer to it when you get stuck: https://en.wikipedia.org/wiki/Hashlife
- also: https://web.archive.org/web/20220131050938/https://jennyhasahat.github.io/hashlife.html


* from chat gpt - i think that possibly what is going on in CreateTree is the hash caching and what is going on with the quick_cache is memoization

 * Hash Caching in Hashlife:

In Hashlife, each unique configuration of a quadtree node is hashed to generate a hash key. The hash key represents the state or configuration of the node.
The hash key is used as a lookup in a hash table or cache, where the previously computed results are stored.
If the result for a particular node configuration is found in the cache, it can be retrieved directly without recomputing it.
Hash caching in Hashlife helps avoid redundant computations by reusing the results for previously encountered node configurations.

Memoization in Hashlife:

Hashlife employs memoization to store intermediate results of the simulation, specifically for the generations in between the power-of-two steps.
During the simulation, when computing the next generation of a node, the algorithm checks if the result for that specific node and the current generation has already been memoized.
If the result is found in the memoization cache, it can be retrieved directly without recomputing it.
Memoization in Hashlife allows the algorithm to reuse the intermediate results for generations that have already been computed, avoiding redundant computations and significantly speeding up the simulation.

 */

/* 
   to facilitate migration, create a Cache or HashMapManager and create tests for it that prove that it's working
   including creating the two implementations and making sure that both return the same results

  to migrate to a newHashMap that allows us to jettison hashmapNext and simplify the code, you can use this to compare
 * This method should now work correctly with the linked list structure in the old hashmap. It iterates through the linked 
 * list of nodes associated with each integer key and compares their populations with the corresponding NodeKey in the new hashmap. 
 * If all nodes match and have equal populations, the method returns true.
 * 
 
 public boolean compareHashmaps(HashMap<Integer, patterning.Node> oldHashmap, HashMap<NodeKey, patterning.Node> newHashmap) {
    int oldHashmapNodeCount = 0;
    for (Map.Entry<Integer, patterning.Node> oldEntry : oldHashmap.entrySet()) {
        patterning.Node oldNode = oldEntry.getValue();

        while (oldNode != null) {
            oldHashmapNodeCount++;

            // Check if there's an equivalent NodeKey in the new hashmap
            NodeKey keyToFind = new NodeKey(oldNode.nw, oldNode.ne, oldNode.sw, oldNode.se, oldEntry.getKey());
            patterning.Node newNode = newHashmap.get(keyToFind);

            // If there's no equivalent NodeKey or the populations are different, return false
            if (newNode == null || !oldNode.population.equals(newNode.population)) {
                return false;
            }

            oldNode = oldNode.hashmapNext;
        }
    }

    return oldHashmapNodeCount == newHashmap.size();
}

use the following to make the new tree side by side with the old (probably use different name - like createTree new - and call it from within createTree)
update names to use the new hashmap<NodeKey, patterning.Node>

private patterning.Node createTree(patterning.Node nw, patterning.Node ne, patterning.Node sw, patterning.Node se) {
    int hash = calcHash(nw.id, ne.id, sw.id, se.id) & hashmapSize;
    
    // Create a key object to store the nodes and hash
    NodeKey key = new NodeKey(nw, ne, sw, se, hash);
    
    // Check if the hashmap contains the key, if so return the corresponding node
    if (hashmap.containsKey(key)) {
        return hashmap.get(key);
    }
    
    // If lastId exceeds maxLoad, garbageCollect and try again
    // you can skip this bit until you've actually replaced the old one...
    if (lastId > maxLoad) {
        garbageCollect();
        return createTree(nw, ne, sw, se);
    }
    
    // Create a new node and put it in the hashmap
    patterning.Node newNode = new patterning.Node(nw, ne, sw, se, lastId++, this.step);
    hashmap.put(key, newNode);
    
    return newNode;
}

// Define NodeKey class
class NodeKey {
    patterning.Node nw, ne, sw, se;
    int hash;

    NodeKey(patterning.Node nw, patterning.Node ne, patterning.Node sw, patterning.Node se, int hash) {
        this.nw = nw;
        this.ne = ne;
        this.sw = sw;
        this.se = se;
        this.hash = hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeKey) {
            NodeKey other = (NodeKey) obj;
            return nw == other.nw && ne == other.ne && sw == other.sw && se == other.se;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}

consider asking about using MurmurHash if you want fewer hash collisions to start


 */

public class LifeUniverse {
    private static final double LOAD_FACTOR = 0.95;
    private static final int INITIAL_SIZE = 16;

    // this is extremely large but can be maybe reached if you fix problems with
    // nodeQuickNextGeneration
    // going to try a maximum universe size of 1024 with a new drawing scheme that relies
    // on calculating relating a viewport to the level being drawn and relies on the max double value
    // the largest level this can support would be  (from Wolfram)  "input": "Floor[Log2[1.8*10^308]]" "output": "1024"
    
    private static final int UNIVERSE_LEVEL_LIMIT = 1024;
    private static final int HASHMAP_LIMIT = 30;
    private static final int MASK_LEFT = 1;
    private static final int MASK_TOP = 2;
    private static final int MASK_RIGHT = 4;
    private static final int MASK_BOTTOM = 8;
    private static final BigInteger[] _powers = new BigInteger[UNIVERSE_LEVEL_LIMIT];

    static {// the size of the MathContext
        _powers[0] = BigInteger.ONE;

        for (int i = 1; i < UNIVERSE_LEVEL_LIMIT; i++) {
            _powers[i] = _powers[i - 1].multiply(BigInteger.TWO);
        }
    }

    // return the cached power of 2 - for performance reasons
    public static BigInteger pow2(int x) {
        if (x >= UNIVERSE_LEVEL_LIMIT) {
            return BigInteger.valueOf(2).pow(UNIVERSE_LEVEL_LIMIT);
        }
        return _powers[x];
    }

    public int lastId;
    private int hashmapSize;
    public int maxLoad;
    private HashMap<Integer, Node> hashmap;
    private Node[] emptyTreeCache;
    private HashMap<Integer, Node> level2Cache;

    private final byte[] _bitcounts;
    private final int rule_b;
    private final int rule_s;
    public Node root;
    public int step;
    public BigInteger generation;

    private final Node falseLeaf;
    private final Node trueLeaf;

    private final PatternInfo patternInfo;

    LifeUniverse() {
        patternInfo = new PatternInfo();
        this._bitcounts = new byte[0x758];
        this._bitcounts[0] = 0;
        this._bitcounts[1] = 1;
        this._bitcounts[2] = 1;
        this._bitcounts[3] = 2;
        this._bitcounts[4] = 1;
        this._bitcounts[5] = 2;
        this._bitcounts[6] = 2;
        this._bitcounts[7] = 3;
        this._bitcounts[8] = 1;
        this._bitcounts[9] = 2;
        this._bitcounts[10] = 2;
        this._bitcounts[11] = 3;
        this._bitcounts[12] = 2;
        this._bitcounts[13] = 3;
        this._bitcounts[14] = 3;
        this._bitcounts[15] = 4;

        for (int i = 0x10; i < 0x758; i++) {
            this._bitcounts[i] = (byte) (this._bitcounts[i & 0xF] + this._bitcounts[i >> 4 & 0xF]
                    + this._bitcounts[i >> 8]);
        }

        // current rule setting
        this.rule_b = 1 << 3;
        this.rule_s = 1 << 2 | 1 << 3;

        this.root = null;

        // number of generations to calculate at one time, written as 2^n
        this.step = 0;

        // in which generation are we
        this.generation = BigInteger.ZERO;

        this.falseLeaf = new Node(3, BigInteger.ZERO, 0);
        this.trueLeaf = new Node(2, BigInteger.ONE, 0);

        // the final necessary setup bits

        // last id for nodes
        this.lastId = 4;

        // Size of the hashmap - Always a power of 2 minus 1
        this.hashmapSize = (1 << INITIAL_SIZE) - 1;

        // Size when the next GC will happen
        this.maxLoad = (int) (this.hashmapSize * LOAD_FACTOR);
        this.hashmap = new HashMap<>();
        this.emptyTreeCache = new Node[UNIVERSE_LEVEL_LIMIT];
        this.level2Cache = new HashMap<>(0x10000);
        this.root = this.emptyTree(3);
        this.generation = BigInteger.ZERO;
        this.step = 0;
    }

    public PatternInfo getPatternInfo() {
        return patternInfo;
    }

    /*
     * Note that Java doesn't have a bitwise logical shift operator
     * that preserves the sign bit like JavaScript (>>),
     * so we can use the unsigned right shift operator (>>>) instead.
     * However, since the _bitcounts array contains only positive values,
     * we can use the regular right shift operator (>>) instead without any issues.
     */
    private int evalMask(int bitmask) {
        int rule = ((bitmask & 32) != 0) ? this.rule_s : this.rule_b;
        return (rule >> this._bitcounts[bitmask & 0x757]) & 1;
    }

    private Node level1Create(int bitmask) {
        return createTree(
                (bitmask & 1) != 0 ? trueLeaf : falseLeaf,
                (bitmask & 2) != 0 ? trueLeaf : falseLeaf,
                (bitmask & 4) != 0 ? trueLeaf : falseLeaf,
                (bitmask & 8) != 0 ? trueLeaf : falseLeaf);
    }

    /*
     * // these were used in the original to draw on screen - maybe you'll get there
     * someday...
     * private void setBit(BigInteger x, BigInteger y, boolean living) {
     * int level = getLevelFromBounds(new patterning.Bounds(y, x));
     * 
     * if (living) {
     * while (level > root.level) {
     * root = expandUniverse(root);
     * }
     * } else {
     * if (level > root.level) {
     * // no need to delete pixels outside the universe
     * return;
     * }
     * }
     * 
     * root = nodeSetBit(root, x, y, living);
     * }
     * 
     * 
     * public boolean getBit(BigInteger x, BigInteger y) {
     * int level = getLevelFromBounds(new patterning.Bounds(y, x));
     * 
     * if (level > root.level) {
     * return false;
     * } else {
     * return nodeGetBit(root, x, y);
     * }
     * }
     */



/*    public void makeCenter(IntBuffer fieldX, IntBuffer fieldY, Bounds bounds) {
        BigInteger offsetX = bounds.left.subtract(bounds.right)
                .divide(BigInteger.valueOf(2))
                .subtract(bounds.left)
                .negate();
        BigInteger offsetY = bounds.top.subtract(bounds.bottom)
                .divide(BigInteger.valueOf(2))
                .subtract(bounds.top)
                .negate();

        moveField(fieldX, fieldY, offsetX.intValue(), offsetY.intValue());

        bounds.left = bounds.left.add(offsetX);
        bounds.right = bounds.right.add(offsetX);
        bounds.top = bounds.top.add(offsetY);
        bounds.bottom = bounds.bottom.add(offsetY);
    }*/

    public void moveField(IntBuffer fieldX, IntBuffer fieldY, int offsetX, int offsetY) {
        for (int i = 0; i < fieldX.capacity(); i++) {
            int x = fieldX.get(i);
            int y = fieldY.get(i);

            fieldX.put(i, x + offsetX);
            fieldY.put(i, y + offsetY);
        }
    }

    private Node emptyTree(int level) {
        if (emptyTreeCache[level] != null) {
            return emptyTreeCache[level];
        }

        Node t;

        if (level == 1) {
            t = falseLeaf;
        } else {
            t = emptyTree(level - 1);
        }

        emptyTreeCache[level] = createTree(t, t, t, t);

        return emptyTreeCache[level];
    }

    /*
     * In the expandUniverse method, a new root node will be created at one level
     * higher than the current root node
     * surrounded by empty space, essentially making the universe double sized (on
     * either dimension)
     *
     * a new subtree is created for each quadrant. Quadrants have a level one below
     * the passed in node but createTree will combine that with the empty trees and
     * create nodes at the same
     * level as the input node.
     *
     * then all 4 will be combined into a new, larger universe by the outside
     * createTree which will return a new
     * node with the prior quadrants moved towards the center of the new tree -
     * creating space at the edges for
     * additional growth
     *
     * Here's the process of creating the new root node with increased level:
     *
     * Create an empty tree t with level node.level - 1.
     *
     * Create new trees for each quadrant, with the original node's
     * quadrants shifted to the corner and empty space added to the other corners.
     *
     * Combine these new trees using the createTree method.
     *
     *
     * So, the new root node will have a level one greater than the original root
     * node,
     * as it combines the new trees created with an extra level of hierarchy.
     */

    private Node expandUniverse(Node node) {
        // System.out.println("expanding universe");

        Node t = emptyTree(node.level - 1);

        return createTree(
                createTree(t, t, t, node.nw),
                createTree(t, t, node.ne, t),
                createTree(t, node.sw, t, t),
                createTree(node.se, t, t, t)
        );
    }

    // Preserve the tree, but remove all cached
    // generations forward
    // alsoQuick came from the original algorithm and was used when
    // (I believe) that the rule set was changed.
    // right now i don't know why it's otherwise okay to preserve
    // need to think more about this
    private void uncache(boolean alsoQuick) {

        hashmap.values().parallelStream().forEach(node -> {
            if (node != null) {
                node.cache = null;
                node.clearBinaryBitArray();
                node.hashmapNext = null;
                if (alsoQuick) {
                    node.quickCache = null;
                }
            }
        });
    }

    // return false if not in the hash map
    // which means it could be in the linked list associated with the hashmap

    public boolean inHashmap(Node n) {

        int hash = calcHash(n.nw.id, n.ne.id, n.sw.id, n.se.id) & hashmapSize;
        Node node = hashmap.get(hash);

        while (node != null) {
            if (node.equals(n)) {
                return true;
            }
            node = node.hashmapNext;
        }

        return false;
    }

    /*
     * The nodeHash method is called during garbage collection, and its main purpose
     * is to ensure
     * that all nodes in the given tree are present in the hashmap. It recursively
     * traverses the
     * tree and calls hashmapInsert to insert each node into the hashmap if it's not
     * already there.
     * 
     * The inHashmap method checks if the node is already in the hashmap (including
     * the linked
     * list of nodes in case of hash collisions). If a node is already in the
     * hashmap, the
     * nodeHash method won't insert it again.
     * 
     * The hashmapInsert method is responsible for inserting a given node n into the
     * hashmap.
     * It first calculates the hash for the node and finds the corresponding entry
     * in the hashmap.
     * If there are any hash collisions, it follows the linked list (using the
     * hashmapNext field)
     * to the last node in the list and inserts the new node there.
     * 
     * So, in summary, the nodeHash method is responsible for ensuring that all
     * nodes in a given
     * tree are present in the hashmap during garbage collection. It does not
     * repopulate a cleared
     * hashmap with a tree, but rather ensures that the existing nodes are correctly
     * inserted
     * into the hashmap. The linked list resulting from hash collisions is
     * maintained by the hashmapInsert method.
     */

    // called only on garbageCollect
    public void nodeHash(Node node) {
        if (!this.inHashmap(node)) {
            // Update the id. We have looked for an old id, as
            // the hashmap has been cleared and ids have been
            // reset, but this cannot be avoided without iterating
            // the tree twice.
            node.id = lastId++;
            node.hashmapNext = null;

            if (node.level > 1) {
                nodeHash(node.nw);
                nodeHash(node.ne);
                nodeHash(node.sw);
                nodeHash(node.se);

                if (node.cache != null) {
                    nodeHash(node.cache);
                }
                if (node.quickCache != null) {
                    nodeHash(node.quickCache);
                }
            }

            hashmapInsert(node);
        }
    }

    // insert a node into the hashmap
    public void hashmapInsert(Node n) {
        int hash = calcHash(n.nw.id, n.ne.id, n.sw.id, n.se.id) & hashmapSize;
        Node node = hashmap.get(hash);
        Node prev = null;

        while (node != null) {
            prev = node;
            node = node.hashmapNext;
        }

        if (prev != null) {
            prev.hashmapNext = n;
        } else {
            hashmap.put(hash, n);
        }
    }

    public void garbageCollect() {
        // if rewinding just keep things as they are
        if (hashmapSize < (1 << HASHMAP_LIMIT) - 1) {
            hashmapSize = (hashmapSize << 1) | 1;
            hashmap = new HashMap<>(hashmapSize + 1);
        }
        maxLoad = (int) (hashmapSize * LOAD_FACTOR);
        hashmap.clear();

        lastId = 4;
        nodeHash(root);

    }

    /*
     * // the hash function used for the hashmap
     * int calcHash(int nw_id, int ne_id, int sw_id, int se_id) {
     * return ((nw_id * 23 ^ ne_id) * 23 ^ sw_id) * 23 ^ se_id;
     * }
     */

    int calcHash(int nw_id, int ne_id, int sw_id, int se_id) {
        int result = 17;
        result = 31 * result ^ nw_id;
        result = 31 * result ^ ne_id;
        result = 31 * result ^ sw_id;
        result = 31 * result ^ se_id;
        return result;
    }

    // this is just used when setting up the field initially unless I'm missing
    // something
    public Bounds getBounds(IntBuffer fieldX, IntBuffer fieldY) {
        if (fieldX.capacity() == 0) {
            return new Bounds(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        }

        // this sets up a bounds that just has an initial top and bottom that are the
        // same and left and right the same
        // the RLEBuffer creates x and y to be the same size regardless of source width
        // and height - it will make it
        // the size of the bounding box
        Bounds bounds = new Bounds(BigInteger.valueOf(fieldY.get(0)), BigInteger.valueOf(fieldX.get(0)));
        int len = fieldX.capacity();

        // todo: pass in varied width and heights and look at the field size they
        // actually generate
        for (int i = 1; i < len; i++) {
            BigInteger x = BigInteger.valueOf(fieldX.get(i));
            BigInteger y = BigInteger.valueOf(fieldY.get(i));

            if (x.compareTo(bounds.left) < 0) {
                bounds.left = x;
            } else if (x.compareTo(bounds.right) > 0) {
                bounds.right = x;
            }

            if (y.compareTo(bounds.top) < 0) {
                bounds.top = y;
            } else if (y.compareTo(bounds.bottom) > 0) {
                bounds.bottom = y;
            }
        }

        return bounds;
    }

    public int getLevelFromBounds(Bounds bounds) {
        int max = 4;
        String[] keys = { "top", "left", "bottom", "right" };

        for (String key : keys) {
            BigInteger coordinate = BigInteger.ZERO;

            switch (key) {
                case "top" -> coordinate = bounds.top;
                case "left" -> coordinate = bounds.left;
                case "bottom" -> coordinate = bounds.bottom;
                case "right" -> coordinate = bounds.right;
                default -> {
                }
            }

            if (coordinate.add(BigInteger.ONE).compareTo(BigInteger.valueOf(max)) > 0) {
                max = coordinate.add(BigInteger.ONE).intValue();
            } else if (coordinate.negate().compareTo(BigInteger.valueOf(max)) > 0) {
                max = coordinate.negate().intValue();
            }
        }

        return (int) Math.ceil(Math.log(max) / Math.log(2)) + 1;
    }

    public void setupField(IntBuffer fieldX, IntBuffer fieldY) {

        Bounds bounds = getBounds(fieldX, fieldY);
        int level = getLevelFromBounds(bounds);

        BigInteger offset = BigInteger.valueOf(2).pow(level - 1);

        int count = fieldX.capacity();

        moveField(fieldX, fieldY, offset.intValue(), offset.intValue());

        root = setupFieldRecurse(0, count - 1, fieldX, fieldY, level);

        updatePatternInfo(true);
    }

    private int partition(int start, int end, IntBuffer testField, IntBuffer otherField, int offset) {
        int i = start, j = end, swap;

        while (i <= j) {
            while (i <= end && (testField.get(i) & offset) == 0) {
                i++;
            }
            while (j > start && (testField.get(j) & offset) != 0) {
                j--;
            }
            if (i >= j) {
                break;
            }
            swap = testField.get(i);
            testField.put(i, testField.get(j));
            testField.put(j, swap);

            swap = otherField.get(i);
            otherField.put(i, otherField.get(j));
            otherField.put(j, swap);

            i++;
            j--;
        }

        return i;
    }

    public Node setupFieldRecurse(int start, int end, IntBuffer fieldX, IntBuffer fieldY, int level) {
        if (start > end) {
            return emptyTree(level);
        }
        if (level == 2) {
            return level2Setup(start, end, fieldX, fieldY);
        }
        level--;
        int offset = 1 << level;
        int part3 = partition(start, end, fieldY, fieldX, offset);
        int part2 = partition(start, part3 - 1, fieldX, fieldY, offset);
        int part4 = partition(part3, end, fieldX, fieldY, offset);

        return createTree(
                setupFieldRecurse(start, part2 - 1, fieldX, fieldY, level),
                setupFieldRecurse(part2, part3 - 1, fieldX, fieldY, level),
                setupFieldRecurse(part3, part4 - 1, fieldX, fieldY, level),
                setupFieldRecurse(part4, end, fieldX, fieldY, level));
    }

    public Node level2Setup(int start, int end, IntBuffer fieldX, IntBuffer fieldY) {
        int set = 0, x, y;

        for (int i = start; i <= end; i++) {
            x = fieldX.get(i);
            y = fieldY.get(i);

            // interleave 2-bit x and y values
            set |= 1 << (x & 1 | (y & 1 | x & 2) << 1 | (y & 2) << 2);
        }

        if (level2Cache.containsKey(set)) {
            return level2Cache.get(set);
        }

        Node tree = createTree(
                level1Create(set),
                level1Create(set >> 4),
                level1Create(set >> 8),
                level1Create(set >> 12));

        level2Cache.put(set, tree);
        return tree;
    }

    public void setStep(int step) {

            if (step != this.step) {

            this.step = step;

            uncache(false);

            // todo: why did this originally exist - it seems empty trees are all the same
            emptyTreeCache = new Node[UNIVERSE_LEVEL_LIMIT];
           // level2Cache = new HashMap<>(0x10000); // it seems level2cache is only used on setting up the life form so maybe they were just taking the opportunity to clear it here?
        }

    }

    /*
     * public void setRules(int s, int b) {
     * if (this.rule_s != s || this.rule_b != b) {
     * this.rule_s = s;
     * this.rule_b = b;
     * 
     * this.uncache(true);
     * emptyTreeCache = new patterning.Node[UNIVERSE_SIZE_LIMIT];
     * level2Cache = new HashMap<>(0x10000);
     * }
     * }
     */

    /*
     * private patterning.Node nodeSetBit(patterning.Node node, BigInteger x, BigInteger y, boolean
     * living) {
     * 
     * if (node.level == 0) {
     * return living ? trueLeaf : falseLeaf;
     * }
     * 
     * BigInteger offset = node.level == 1 ? BigInteger.ZERO : pow2(node.level - 2);
     * 
     * patterning.Node nw = node.nw, ne = node.ne, sw = node.sw, se = node.se;
     * 
     * if (x.compareTo(BigInteger.ZERO) < 0) {
     * if (y.compareTo(BigInteger.ZERO) < 0) {
     * nw = nodeSetBit(nw, x.add(offset), y.add(offset), living);
     * } else {
     * sw = nodeSetBit(sw, x.add(offset), y.subtract(offset), living);
     * }
     * } else {
     * if (y.compareTo(BigInteger.ZERO) < 0) {
     * ne = nodeSetBit(ne, x.subtract(offset), y.add(offset), living);
     * } else {
     * se = nodeSetBit(se, x.subtract(offset), y.subtract(offset), living);
     * }
     * }
     * 
     * return createTree(nw, ne, sw, se);
     * }
     * 
     * private boolean nodeGetBit(patterning.Node node, BigInteger x, BigInteger y) {
     * if (node.population.equals(BigInteger.ZERO)) {
     * return false;
     * }
     * 
     * if (node.level == 0) {
     * // other level 0 case is handled above
     * return true;
     * }
     * 
     * BigInteger offset = node.level == 1 ? BigInteger.ZERO : pow2(node.level - 2);
     * 
     * if (x.compareTo(BigInteger.ZERO) < 0) {
     * if (y.compareTo(BigInteger.ZERO) < 0) {
     * return nodeGetBit(node.nw, x.add(offset), y.add(offset));
     * } else {
     * return nodeGetBit(node.sw, x.add(offset), y.subtract(offset));
     * }
     * } else {
     * if (y.compareTo(BigInteger.ZERO) < 0) {
     * return nodeGetBit(node.ne, x.subtract(offset), y.add(offset));
     * } else {
     * return nodeGetBit(node.se, x.subtract(offset), y.subtract(offset));
     * }
     * }
     * }
     */

    public Node node_level2_next(Node node) {
        Node nw = node.nw;
        Node ne = node.ne;
        Node sw = node.sw;
        Node se = node.se;

        BigInteger bitmask = nw.nw.population.shiftLeft(15).or(nw.ne.population.shiftLeft(14))
                .or(ne.nw.population.shiftLeft(13)).or(ne.ne.population.shiftLeft(12))
                .or(nw.sw.population.shiftLeft(11)).or(nw.se.population.shiftLeft(10)).or(ne.sw.population.shiftLeft(9))
                .or(ne.se.population.shiftLeft(8))
                .or(sw.nw.population.shiftLeft(7)).or(sw.ne.population.shiftLeft(6)).or(se.nw.population.shiftLeft(5))
                .or(se.ne.population.shiftLeft(4))
                .or(sw.sw.population.shiftLeft(3)).or(sw.se.population.shiftLeft(2)).or(se.sw.population.shiftLeft(1))
                .or(se.se.population);

        int result = evalMask(bitmask.shiftRight(5).intValue()) |
                evalMask(bitmask.shiftRight(4).intValue()) << 1 |
                evalMask(bitmask.shiftRight(1).intValue()) << 2 |
                evalMask(bitmask.intValue()) << 3;

        return level1Create(result);
    }

    // create or search for a tree node given its children
    private Node createTree(Node nw, Node ne, Node sw, Node se) {

        int hash = calcHash(nw.id, ne.id, sw.id, se.id) & hashmapSize;
        Node node = hashmap.get(hash);
        Node prev = null;

        while (node != null) {
            if (node.nw == nw && node.ne == ne && node.sw == sw && node.se == se) {
                return node;
            }
            prev = node;
            node = node.hashmapNext;
        }

        if (lastId > maxLoad) {
            garbageCollect();
            // garbageCollect new maxLoad:{String.format("%,d", maxLoad)} - next thing up is
            // returning a new tree because createTree was about to just make a new one, but
            // now it's calling itself recursively rather than just making a new node and
            // returning it
            return createTree(nw, ne, sw, se);
        }

        Node newNode = new Node(nw, ne, sw, se, lastId++, this.step);
        if (prev != null) {
            prev.hashmapNext = newNode;
        } else {
            hashmap.put(hash, newNode);
        }

        return newNode;
    }

    public void nextGeneration() {
        Node root = this.root;

       /* // the following is a new mechanism to only expandUniverse as far as you need to
        // and not just merely to match up to the current step size
        // this seems to work but if it ever doesn't this might be the culprit
        // you  have to go back to
        // this as the first line of the while below:
        // while ((root.level <= this.step + 2) ||

        // Get the current patterning.Bounds object for live cells in root
        patterning.Bounds bounds = getRootBounds();

        // Calculate the maximum dimension of the live cells' bounding box
        BigInteger width = bounds.right.subtract(bounds.left);
        BigInteger height = bounds.bottom.subtract(bounds.top);
        BigInteger maxDimension = width.max(height);

        // Calculate the required universe size based on the maximum dimension
        int requiredLevel = root.level;
        BigInteger size = pow2(root.level + 1);
        while (size.compareTo(maxDimension) < 0) {
            requiredLevel++;
            size = pow2(requiredLevel + 1);
        }*

        // when we're not large enough to fit the bounds, or the tree needs to be
        // repositioned towards the center, then expand universe until that's true
        while (root.level < requiredLevel ||/
        */


        /* 
         * so the above didn't work - chatGPT suggested the following:
         * Your commented-out proposed optimization seems to be trying to calculate
         *  the minimum required level of the root node based on the size of the bounding 
         * box of the live cells. This could be a good optimization if the size of the live
         *  area is typically much smaller than the size of the entire universe. However, 
         * it's possible that this broke your algorithm because the level of the root node 
         * ended up being too small for the number of generations you're trying to advance at once.

            To create a test for this issue, you could create a pattern that grows
             significantly over many generations, such as a glider gun in the Game of Life.
              Then, you could create a test that steps this pattern forward by a large number
               of generations and checks the resulting state against a known correct result.
                This could help you figure out if the issue is with the size of the root node or with recentering the universe.
         */

        while ( (root.level <= this.step + 2) || 
                !root.nw.population.equals(root.nw.se.se.population) ||
                !root.ne.population.equals(root.ne.sw.sw.population) ||
                !root.sw.population.equals(root.sw.ne.ne.population) ||
                !root.se.population.equals(root.se.nw.nw.population)) {
            root = this.expandUniverse(root);
        }
        // patterning.Node maintains a per generation set of nodes that were newly created
        // clear them so the drawing routine can use unchanged nodes as a key to a cache
        // associated with their
        // drawing
        Node.clearChanged();

        this.root = nodeNextGeneration(root);

        BigInteger generationIncrease = pow2(this.step); // BigInteger.valueOf(2).pow(this.step);

        this.generation = this.generation.add(generationIncrease);

        // there are some patterns that got down completely
        // after a short while to be able to do getRootBounds()
        // other similarly large patterns don't have this issue
        // what's the story?
        updatePatternInfo(true);
    }

    private void updatePatternInfo(boolean everything) {

        patternInfo.addOrUpdate("level", root.level);
        patternInfo.addOrUpdate("step", LifeUniverse.pow2(step));
        patternInfo.addOrUpdate("generation", generation);
        patternInfo.addOrUpdate("population", root.population);
        patternInfo.addOrUpdate("maxLoad", maxLoad);
        patternInfo.addOrUpdate("lastId", lastId);

        if (everything) {
            Bounds bounds = getRootBounds();
            patternInfo.addOrUpdate("width", bounds.right.subtract(bounds.left).add(BigInteger.ONE));
            patternInfo.addOrUpdate("height", bounds.bottom.subtract(bounds.top).add(BigInteger.ONE));
        }
    }

    private Node nodeNextGeneration(Node node) {

        if (node.cache != null) {
            return node.cache;
        }

        if (this.step == node.level - 2) {
            quickgen = 0;
            // System.out.println("root.level: " + root.level + " step: " + this.step + "
            // node.level: " + node.level);
           return nodeQuickNextGeneration(node, 0);
        }

        // right now i have seen a nodeNextGeneration where
        // node.level == 2 so...
        // maybe this doesn't need to exist?
        // todo: see what happens when you have a blank canvas and use setbit...
        if (node.level == 2) {
            if (node.quickCache == null) {
                node.quickCache = node_level2_next(node);
            }
            return node.quickCache;
        }

        Node nw = node.nw;
        Node ne = node.ne;
        Node sw = node.sw;
        Node se = node.se;

        Node n00 = createTree(nw.nw.se, nw.ne.sw, nw.sw.ne, nw.se.nw);
        Node n01 = createTree(nw.ne.se, ne.nw.sw, nw.se.ne, ne.sw.nw);
        Node n02 = createTree(ne.nw.se, ne.ne.sw, ne.sw.ne, ne.se.nw);
        Node n10 = createTree(nw.sw.se, nw.se.sw, sw.nw.ne, sw.ne.nw);
        Node n11 = createTree(nw.se.se, ne.sw.sw, sw.ne.ne, se.nw.nw);
        Node n12 = createTree(ne.sw.se, ne.se.sw, se.nw.ne, se.ne.nw);
        Node n20 = createTree(sw.nw.se, sw.ne.sw, sw.sw.ne, sw.se.nw);
        Node n21 = createTree(sw.ne.se, se.nw.sw, sw.se.ne, se.sw.nw);
        Node n22 = createTree(se.nw.se, se.ne.sw, se.sw.ne, se.se.nw);

        Node newNW = nodeNextGeneration(createTree(n00, n01, n10, n11));
        Node newNE = nodeNextGeneration(createTree(n01, n02, n11, n12));
        Node newSW = nodeNextGeneration(createTree(n10, n11, n20, n21));
        Node newSE = nodeNextGeneration(createTree(n11, n12, n21, n22));

        Node result = createTree(newNW, newNE, newSW, newSE);

        // cascade it up the tree
        Node.addChanged(node);
        // mark the new one changed
        Node.addChanged(result);

        node.cache = result;



        return result;
    }

    private long quickgen;

    public Node nodeQuickNextGeneration(Node node, int depth) throws IllegalStateException {
        quickgen += 1;

        if (node.quickCache != null) {
            return node.quickCache;
        }

        if (node.level == 2) {
            return node.quickCache = this.node_level2_next(node);
        }

        Node nw = node.nw;
        Node ne = node.ne;
        Node sw = node.sw;
        Node se = node.se;

        depth = depth + 1;

        Node n00 = this.nodeQuickNextGeneration(nw, depth);
        Node n01 = this.nodeQuickNextGeneration(createTree(nw.ne, ne.nw, nw.se, ne.sw),
                depth);
        Node n02 = this.nodeQuickNextGeneration(ne, depth);
        Node n10 = this.nodeQuickNextGeneration(createTree(nw.sw, nw.se, sw.nw, sw.ne),
                depth);
        Node n11 = this.nodeQuickNextGeneration(createTree(nw.se, ne.sw, sw.ne, se.nw),
                depth);
        Node n12 = this.nodeQuickNextGeneration(createTree(ne.sw, ne.se, se.nw, se.ne),
                depth);
        Node n20 = this.nodeQuickNextGeneration(sw, depth);
        Node n21 = this.nodeQuickNextGeneration(createTree(sw.ne, se.nw, sw.se, se.sw),
                depth);
        Node n22 = this.nodeQuickNextGeneration(se, depth);

        return node.quickCache = this.createTree(
                this.nodeQuickNextGeneration(createTree(n00, n01, n10, n11), depth),
                this.nodeQuickNextGeneration(createTree(n01, n02, n11, n12), depth),
                this.nodeQuickNextGeneration(createTree(n10, n11, n20, n21), depth),
                this.nodeQuickNextGeneration(createTree(n11, n12, n21, n22), depth));
    }

   public Bounds getRootBounds() {
        if (root.population.equals(BigInteger.ZERO)) {
            return new Bounds(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        }

       // BigInteger offset = BigInteger.valueOf(2).pow(root.level - 1);
       BigInteger offset = pow2(root.level-1);

       Bounds bounds = new Bounds(offset, offset, offset.negate(), offset.negate());

       nodeGetBoundary(root, offset.negate(), offset.negate(),
                MASK_TOP | MASK_LEFT | MASK_BOTTOM | MASK_RIGHT, bounds);

       return bounds;
    }

    private void nodeGetBoundary(Node node, BigInteger left, BigInteger top, int findMask, Bounds  boundary) {
        if (node.population.equals(BigInteger.ZERO) || findMask == 0) {
            return;
        }

        if (node.level == 0) {
                boundary.left = boundary.left.min(left);
                boundary.right = boundary.right.max(left);
                boundary.top = boundary.top.min(top);
                boundary.bottom = boundary.bottom.max(top);
        } else {
            BigInteger offset = pow2(node.level - 1);
            BigInteger doubledOffset = pow2(node.level);

            if (left.compareTo(boundary.left) >= 0
                    && left.add(doubledOffset).compareTo(boundary.right) <= 0 &&
                    top.compareTo(boundary.top) >= 0
                    && top.add(doubledOffset).compareTo(boundary.bottom) <= 0) {
                // This square is already inside the found boundary
                return;
            }

            int findNW = findMask;
            int findSW = findMask;
            int findNE = findMask;
            int findSE = findMask;

            if (!node.nw.population.equals(BigInteger.ZERO)) {
                findSW &= ~MASK_TOP;
                findNE &= ~MASK_LEFT;
                findSE &= ~(MASK_TOP | MASK_LEFT);
            }
            if (!node.sw.population.equals(BigInteger.ZERO)) {
                findSE &= ~MASK_LEFT;
                findNW &= ~MASK_BOTTOM;
                findNE &= ~(MASK_BOTTOM | MASK_LEFT);
            }
            if (!node.ne.population.equals(BigInteger.ZERO)) {
                findNW &= ~MASK_RIGHT;
                findSE &= ~MASK_TOP;
                findSW &= ~(MASK_TOP | MASK_RIGHT);
            }
            if (!node.se.population.equals(BigInteger.ZERO)) {
                findSW &= ~MASK_RIGHT;
                findNE &= ~MASK_BOTTOM;
                findNW &= ~(MASK_BOTTOM | MASK_RIGHT);
            }

            nodeGetBoundary(node.nw, left, top, findNW, boundary);
            nodeGetBoundary(node.sw, left, top.add(offset), findSW, boundary);
            nodeGetBoundary(node.ne, left.add(offset), top, findNE, boundary);
            nodeGetBoundary(node.se, left.add(offset), top.add(offset), findSE, boundary);
        }
    }

    private void newNodeGetBoundary(Node node, BigInteger left, BigInteger top, int findMask, Bounds  boundary, Object lock) {
        if (node.population.equals(BigInteger.ZERO) || findMask == 0) {
            return;
        }

        if (node.level == 0) {
            synchronized (lock) {
                boundary.left = boundary.left.min(left);
                boundary.right = boundary.right.max(left);
                boundary.top = boundary.top.min(top);
                boundary.bottom = boundary.bottom.max(top);
            }
        } else {
            BigInteger offset = pow2(node.level - 1);

            if (left.compareTo(boundary.left) >= 0
                    && left.add(offset.multiply(BigInteger.valueOf(2))).compareTo(boundary.right) <= 0 &&
                    top.compareTo(boundary.top) >= 0
                    && top.add(offset.multiply(BigInteger.valueOf(2))).compareTo(boundary.bottom) <= 0) {
                // This square is already inside the found boundary
                return;
            }

            int findNW = findMask;
            int findSW = findMask;
            int findNE = findMask;
            int findSE = findMask;

            if (!node.nw.population.equals(BigInteger.ZERO)) {
                findSW &= ~MASK_TOP;
                findNE &= ~MASK_LEFT;
                findSE &= ~(MASK_TOP | MASK_LEFT);
            }
            if (!node.sw.population.equals(BigInteger.ZERO)) {
                findSE &= ~MASK_LEFT;
                findNW &= ~MASK_BOTTOM;
                findNE &= ~(MASK_BOTTOM | MASK_LEFT);
            }
            if (!node.ne.population.equals(BigInteger.ZERO)) {
                findNW &= ~MASK_RIGHT;
                findSE &= ~MASK_TOP;
                findSW &= ~(MASK_TOP | MASK_RIGHT);
            }
            if (!node.se.population.equals(BigInteger.ZERO)) {
                findSW &= ~MASK_RIGHT;
                findNE &= ~MASK_BOTTOM;
                findNW &= ~(MASK_BOTTOM | MASK_RIGHT);
            }

            newNodeGetBoundary(node.nw, left, top, findNW, boundary, lock);
            newNodeGetBoundary(node.sw, left, top.add(offset), findSW, boundary, lock);
            newNodeGetBoundary(node.ne, left.add(offset), top, findNE, boundary, lock);
            newNodeGetBoundary(node.se, left.add(offset), top.add(offset), findSE, boundary, lock);
        }
    }
}