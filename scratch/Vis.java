class Vis 
{
public static void main(String[] args) {
 Vis t = new Vis(8);
 t.merge(0, 3);
 t.merge(1, 2);
 t.merge(1, 4);
 t.merge(5, 6);
 t.merge(3, 4);
 t.merge(7, 5);}
 private Node[] links;
 private class Node
 {
 private Node next;
 }
 public Vis(int N)
 {
 links = new Node[N];
 for (int i = 0; i < N; i++)
 links[i] = new Node();
 }
 private Node root(int i)
 {
 Node x = links[i];
 while (x.next != null) x = x.next;
 return x;
 }
 public void merge(int i, int j)
 {
 root(i).next = root(j);
 }
 public boolean merged(int i, int j)
 {
 return root(i) == root(j);
 }
}