/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.causalclustering.load_balancing.plugins.server_policies;

import java.util.Objects;
import java.util.Set;

import org.neo4j.helpers.AdvertisedSocketAddress;

/**
 * Hold the server information that is interesting for load balancing purposes.
 */
class ServerInfo
{
    private final AdvertisedSocketAddress boltAddress;
    private Set<String> tags;

    ServerInfo( AdvertisedSocketAddress boltAddress, Set<String> tags )
    {
        this.boltAddress = boltAddress;
        this.tags = tags;
    }

    AdvertisedSocketAddress boltAddress()
    {
        return boltAddress;
    }

    Set<String> tags()
    {
        return tags;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        { return true; }
        if ( o == null || getClass() != o.getClass() )
        { return false; }
        ServerInfo that = (ServerInfo) o;
        return Objects.equals( boltAddress, that.boltAddress ) &&
               Objects.equals( tags, that.tags );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( boltAddress, tags );
    }

    @Override
    public String toString()
    {
        return "ServerInfo{" +
               "boltAddress=" + boltAddress +
               ", tags=" + tags +
               '}';
    }
}
