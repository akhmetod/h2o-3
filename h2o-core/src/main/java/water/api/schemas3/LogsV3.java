package water.api.schemas3;

import water.Iced;
import water.api.API;

public class LogsV3 extends RequestSchemaV3<Iced, LogsV3> {

  @API(help="IP and port of the node to get the logs from.", required = true, direction = API.Direction.INPUT)
  public String ipport;

  @API(help="Which specific log file to read from the log file directory.  If left unspecified, the system chooses a default for you.", direction = API.Direction.INPUT)
  public String name;

  @API(help="Content of log file", direction = API.Direction.OUTPUT)
  public String log;
}
