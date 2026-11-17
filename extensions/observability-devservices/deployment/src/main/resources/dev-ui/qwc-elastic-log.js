import { QwcServerLog } from "qwc-server-log";

/**
 * This component filters the log to only show Elastic related entries.
 */
export class QwcElasticLog extends QwcServerLog {
  doLogEntry(entry) {
    if (entry.loggerName && entry.loggerName.includes("ElasticContainer")) {
      return true;
    }
    return false;
  }
}

customElements.define("qwc-elastic-log", QwcElasticLog);
