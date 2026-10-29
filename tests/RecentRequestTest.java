package fr.streakkeeper.app;
public class RecentRequestTest {
 public static void main(String[] a) {
  long t=1_000_000L;
  check(null,RecentRequest.elapsed(0,t));
  check(null,RecentRequest.elapsed(t,t-1));
  check("0 min",RecentRequest.elapsed(t,t));
  check("5 min",RecentRequest.elapsed(t,t+5*RecentRequest.MINUTE));
  check("59 min",RecentRequest.elapsed(t,t+RecentRequest.HOUR-1));
  check("1 h",RecentRequest.elapsed(t,t+RecentRequest.HOUR));
  check("23 h",RecentRequest.elapsed(t,t+RecentRequest.DAY-1));
  check(null,RecentRequest.elapsed(t,t+RecentRequest.DAY));
  System.out.println("8 vérifications temporelles réussies");
 }
 static void check(String a,String b){if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
}
