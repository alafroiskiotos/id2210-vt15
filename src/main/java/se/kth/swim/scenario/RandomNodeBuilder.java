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

import java.util.Random;

/**
 *
 * @author lorenzocorneo
 */
public class RandomNodeBuilder extends AbstractNodesBuilder{
  public RandomNodeBuilder(Integer size) {
    super(size);
  }

  @Override
  protected void generate() {
    Random r = new Random();
    
    for(int i = 0; i < size; i++) {
      if(r.nextInt(2) == 0) {
        open.add(i);
      } else {
        nated.add(i);
      }
    }
  }
}