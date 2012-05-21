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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 *
 * $Id$
 *
 */
package org.ow2.sirocco.apis.rest.cimi.manager;

import org.ow2.sirocco.apis.rest.cimi.domain.CimiCredentials;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiCredentialsCreate;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiCredentialsTemplate;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiMachineConfiguration;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiMachineCreate;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiMachineImage;
import org.ow2.sirocco.apis.rest.cimi.domain.CimiMachineTemplate;
import org.ow2.sirocco.apis.rest.cimi.request.CimiContext;

/**
 * Interface of a helper to get complete entity passed by reference or by value
 * during its creation
 * <p>
 * For all methods of this interface : the given entity is searched in services
 * only if its reference is known. Then, and only if necessary, the found
 * reference is merged with the given data of entity.
 * </p>
 */
public interface MergeReferenceHelper {

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiCredentials cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiCredentialsCreate cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiCredentialsTemplate cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiMachineCreate cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiMachineConfiguration cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiMachineImage cimi) throws Exception;

    /**
     * Merge the reference of a entity only if necessary.
     * 
     * @param context The working context
     * @param cimi The entity with values or reference
     * @throws Exception If error in call service
     */
    void merge(final CimiContext context, final CimiMachineTemplate cimi) throws Exception;

}