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
package se.kth.swim.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author lorenzocorneo
 */
public abstract class AbstractNodesBuilder implements NodesBuilder{
  protected Integer size;
  protected List<Integer> nated, open;
  
  public AbstractNodesBuilder(Integer size) {
    this.size = size;
    this.open = new ArrayList<>();
    this.nated = new ArrayList<>();
    
    this.buildNodes();
  }
  
  private void buildNodes() {
    generate();
  }
  
  protected abstract void generate();
  
  @Override
  public Integer[] getOpenNodes() {
    return arrayListToArray(open);
  }

  @Override
  public Integer[] getNatedNodes() {
    return arrayListToArray(nated);
  }
  
  private Integer[] arrayListToArray(List<Integer> list) {
    Integer[] ret = new Integer[list.size()];
    
    for(int i = 0; i < list.size(); i++) {
      ret[i] = list.get(i);
    }
    
    return ret;
  }
}