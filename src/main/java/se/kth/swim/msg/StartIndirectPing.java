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
package se.kth.swim.msg;

import java.util.UUID;
import se.kth.swim.Peer;

public class StartIndirectPing extends MessageCounter {
  private final Peer suspectedPeer;
  private final Peer initiatorPeer;
	private final UUID deadPingTimeout;
	
	public StartIndirectPing(Peer initiatorPeer, Peer suspectedPeer, UUID deadPingTimeout, Integer counter) {
		super(counter);
		this.suspectedPeer = suspectedPeer;
    this.initiatorPeer = initiatorPeer;
		this.deadPingTimeout = deadPingTimeout;
	}

	public Peer getSuspectedPeer() {
		return suspectedPeer;
	}

  public Peer getInitiatorPeer() {
    return initiatorPeer;
  }

	public UUID getDeadPingTimeout() {
		return deadPingTimeout;
	}
}
