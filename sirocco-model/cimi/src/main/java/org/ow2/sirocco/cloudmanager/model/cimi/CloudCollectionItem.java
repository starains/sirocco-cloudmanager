/**
 *
 * SIROCCO
 * Copyright (C) 2011 France Telecom
 * Contact: sirocco@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  $Id: CloudResource.java 1258 2012-05-21 12:35:04Z ycas7461 $
 *
 */

package org.ow2.sirocco.cloudmanager.model.cimi;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class CloudCollectionItem extends CloudEntity {
    private static final long serialVersionUID = 1L;

    private CloudResource resource;

    public static enum State {
        NOT_AVAILABLE, AVAILABLE, DELETED, ATTACHING, ATTACHED, DETACHING, DETACHED, ERROR
    }

    private State state;

    @OneToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "cloudcoll_ent_id")
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    public CloudResource getResource() {
        return this.resource;
    }

    public void setResource(final CloudResource resource) {
        this.resource = resource;
    }

    @Enumerated(EnumType.STRING)
    public State getState() {
        return this.state;
    }

    public void setState(final State state) {
        this.state = state;
    }
}
