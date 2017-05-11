package water.api;

import water.*;
import water.api.schemas3.LogsV3;
import water.util.LinuxProcFileReader;
import water.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LogsHandler extends Handler {
  private static class GetLogTask extends DTask<GetLogTask> {
    public String name;
    public String log;

    public boolean success = false;

    public GetLogTask() {
      super(H2O.GUI_PRIORITY);
      log = null;
    }

    public void doIt() {
      String logPathFilename = "/undefined";        // Satisfy IDEA inspection.
      try {
        if (name == null || name.equals("default")) {
          name = "debug";
        }

        if (name.equals("stdout") || name.equals("stderr")) {
          LinuxProcFileReader lpfr = new LinuxProcFileReader();
          lpfr.read();
          if (! lpfr.valid()) {
            log = "This option only works for Linux hosts";
          }
          else {
            String pid = lpfr.getProcessID();
            String fdFileName = "/proc/" + pid + "/fd/" + (name.equals("stdout") ? "1" : "2");
            File f = new File(fdFileName);
            logPathFilename = f.getCanonicalPath();
            if (logPathFilename.startsWith("/dev")) {
              log = "Unsupported when writing to console";
            }
            if (logPathFilename.startsWith("socket")) {
              log = "Unsupported when writing to a socket";
            }
            if (logPathFilename.startsWith("pipe")) {
              log = "Unsupported when writing to a pipe";
            }
            if (logPathFilename.equals(fdFileName)) {
              log = "Unsupported when writing to a pipe";
            }
            Log.trace("LogPathFilename calculation: " + logPathFilename);
          }
        }
        else if (  name.equals("trace")
                || name.equals("debug")
                || name.equals("info")
                || name.equals("warn")
                || name.equals("error")
                || name.equals("fatal")
                || name.equals("httpd")
                ) {
          name = water.util.Log.getLogFileName(name);
          try {
            String logDir = Log.getLogDir();
            logPathFilename = logDir + File.separator + name;
          }
          catch (Exception e) {
            log = "H2O logging not configured.";
          }
        }
        else {
          throw new IllegalArgumentException("Illegal log file name requested (try 'default')");
        }

        if (log == null) {
          File f = new File(logPathFilename);
          if (!f.exists()) {
            throw new IllegalArgumentException("File " + f + " does not exist");
          }
          if (!f.canRead()) {
            throw new IllegalArgumentException("File " + f + " is not readable");
          }

          BufferedReader reader = new BufferedReader(new FileReader(f));
          String line;
          StringBuilder sb = new StringBuilder();

          line = reader.readLine();
          while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = reader.readLine();
          }
          reader.close();

          log = sb.toString();
        }

        success = true;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override public void compute2() {
      doIt();
      tryComplete();
    }
  }

  private static H2ONode.H2Okey getKey(String ipPort){
    if(ipPort.equals("self")){
      return H2O.SELF._key;
    }else {
      String ip = ipPort.split(":")[0];
      int port = Integer.parseInt(ipPort.split(":")[1]) + 1; // use public port

      try {
        return new H2ONode.H2Okey(InetAddress.getByName(ip), port);
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("Ip address of the node to get logs from is not valid!");
      }
    }
  }

  // this method already expects that the key represents either h2o node or client.
  // it still can return null since the client could disappear in the meanwhile
  private static H2ONode getNodeByKey(H2ONode.H2Okey key){
    H2ONode ret = H2O.CLOUD.membersByKey().get(key);
    if(ret == null){
      ret = H2O.getClientsByKey().get(key);
    }
    return ret;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogsV3 fetch(int version, LogsV3 s) {

    final H2ONode.H2Okey key = getKey(s.ipport);
    if(!H2O.CLOUD.membersByKey().containsKey(key) && !H2O.getClientsByKey().containsKey(key)){
      // the key does not represent any existing h2o cloud member or client
      throw new IllegalArgumentException("No H2O node running as part of this cloud on " + s.ipport+ " does not exist!");
    }

    String filename = s.name;
    if (filename != null) {
      if (filename.contains(File.separator)) {
        throw new IllegalArgumentException("Filename may not contain File.separator character.");
      }
    }

    GetLogTask t = new GetLogTask();
    t.name = filename;
    if (H2O.SELF._key.equals(key)) {
      // Local node.
      try {
        t.doIt();
      }
      catch (Exception e) {
        Log.err(e);
      }
    } else {
      // Remote node.
      Log.trace("GetLogTask starting to node  " + key + " ...");
      H2ONode node = getNodeByKey(key);
      new RPC<>(node, t).call().get();
      Log.trace("GetLogTask completed to node " + key);
    }

    if (!t.success) {
      throw new RuntimeException("GetLogTask failed");
    }

    s.log = t.log;

    return s;
  }
}
