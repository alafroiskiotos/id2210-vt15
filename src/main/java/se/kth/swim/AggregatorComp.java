/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    
    private long start;
    private final Integer killingTime, size;
    private final Map<Integer, Status> snapshot;
    private final Integer[] nodeToKill;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        this.killingTime = init.getAfter();
        this.nodeToKill = init.getKilled();
        this.size = init.getSize();
        
        // Init the timestamp for when the nodes will be killed
        // Our assumption is that all the node will be killed at the same time
        this.start = System.currentTimeMillis() + killingTime;
        
        log.info("{} initiating...", new Object[]{selfAddress.getId()});
        
        snapshot = new HashMap<>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
        }

    };

    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
            log.info("{} status from:{} pings:{}, alive:{}, dead:{}", 
                    new Object[]{status.getSource().getId(), status.getHeader().getSource().getId(), 
                      status.getContent().getReceivedPings(), status.getContent().getAliveNodes(),
                    status.getContent().getDeadNodes()});
            
            updateSnapshot(status);
            
            if(convergence()) {
              log.info("CONVERGENCE in {} ms!", System.currentTimeMillis() - start);
            }
        }
    };
    
    private boolean convergence() {
      for(int i = 0; i < snapshot.size(); i++) {
        if(nodeToKill.length > 0) {
          if(snapshot.get(i).getDeadNodes() < nodeToKill.length) {
            return false;
          }
        } else {
          if(snapshot.get(i).getAliveNodes() < size) {
            return false;
          }
        }
      }
      
      return true;
    }
    
    private void updateSnapshot(NetStatus newState) {
      if(snapshot.containsKey(newState.getSource().getId())) {
        snapshot.replace(newState.getSource().getId(), newState.getContent());
      } else {
        // Data from the dead nodes will not be taken.
        if(!Arrays.asList(nodeToKill).contains(newState.getSource().getId())) {
          snapshot.put(newState.getSource().getId(), newState.getContent());
        }
      }
    }

  public static class AggregatorInit extends Init<AggregatorComp> {

    private final NatedAddress selfAddress;
    private final Integer[] killed;
    private final Integer after, size;

    public AggregatorInit(NatedAddress selfAddress, Integer size, Integer[] killed, Integer after) {
        this.selfAddress = selfAddress;
        this.killed = killed;
        this.after = after;
        this.size = size;
    }

    public NatedAddress getSelfAddress() {
      return selfAddress;
    }

    public Integer[] getKilled() {
      return killed;
    }

    public Integer getAfter() {
      return after;
    }

    public Integer getSize() {
      return size;
    }
  }
}
