/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.state;

import java.util.Arrays;
import java.util.Iterator;

import org.neo4j.collection.primitive.PrimitiveIntCollections;
import org.neo4j.collection.primitive.PrimitiveIntSet;
import org.neo4j.cursor.Cursor;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.kernel.api.cursor.EntityItemHelper;
import org.neo4j.kernel.api.cursor.RelationshipItemHelper;
import org.neo4j.kernel.api.properties.DefinedProperty;
import org.neo4j.storageengine.api.NodeItem;
import org.neo4j.storageengine.api.PropertyItem;
import org.neo4j.storageengine.api.RelationshipItem;

import static org.neo4j.collection.primitive.PrimitiveIntCollections.emptySet;
import static org.neo4j.helpers.collection.Iterables.map;
import static org.neo4j.kernel.impl.util.Cursors.empty;

/**
 * Stub cursors to be used for testing.
 */
public class StubCursors
{
    public static Cursor<NodeItem> asNodeCursor( final long nodeId )
    {
        return asNodeCursor( nodeId, empty(), emptySet() );
    }

    public static Cursor<NodeItem> asNodeCursor( final long... nodeIds )
    {
        NodeItem[] nodeItems = new NodeItem[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++)
        {
            nodeItems[i] = new StubNodeItem( nodeIds[i], empty(), emptySet() );
        }
        return cursor( nodeItems );
    }

    public static Cursor<NodeItem> asNodeCursor( final long nodeId,
            final Cursor<PropertyItem> propertyCursor )
    {
        return cursor( new StubNodeItem( nodeId, propertyCursor, emptySet() ) );
    }

    public static Cursor<NodeItem> asNodeCursor( final long nodeId,
            final Cursor<PropertyItem> propertyCursor,
            final PrimitiveIntSet labels )
    {
        return cursor( new StubNodeItem( nodeId, propertyCursor, labels ) );
    }

    private static class StubNodeItem extends EntityItemHelper implements NodeItem
    {
        private final long nodeId;
        private final Cursor<PropertyItem> propertyCursor;
        private final PrimitiveIntSet labelCursor;

        private StubNodeItem( long nodeId, Cursor<PropertyItem> propertyCursor, PrimitiveIntSet labelCursor )
        {
            this.nodeId = nodeId;
            this.propertyCursor = propertyCursor;
            this.labelCursor = labelCursor;
        }

        @Override
        public long id()
        {
            return nodeId;
        }

        @Override
        public boolean hasLabel( int labelId )
        {
            return labelCursor.contains( labelId );
        }

        @Override
        public long nextGroupId()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        @Override
        public long nextRelationshipId()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        @Override
        public PrimitiveIntSet labels()
        {
            return labelCursor;
        }

        @Override
        public Cursor<PropertyItem> property( final int propertyKeyId )
        {
            return new Cursor<PropertyItem>()
            {
                Cursor<PropertyItem> cursor = properties();

                @Override
                public boolean next()
                {
                    while ( cursor.next() )
                    {
                        if ( cursor.get().propertyKeyId() == propertyKeyId )
                        {
                            return true;
                        }
                    }

                    return false;
                }

                @Override
                public void close()
                {
                    cursor.close();
                }

                @Override
                public PropertyItem get()
                {
                    return cursor.get();
                }
            };
        }

        @Override
        public Cursor<PropertyItem> properties()
        {
            return propertyCursor;
        }

        @Override
        public boolean isDense()
        {
            throw new UnsupportedOperationException(  );
        }
    }

    public static Cursor<RelationshipItem> asRelationshipCursor( final long relId, final int type,
            final long startNode, final long endNode, final Cursor<PropertyItem> propertyCursor )
    {
        return cursor( new RelationshipItemHelper()
        {
            @Override
            public long id()
            {
                return relId;
            }

            @Override
            public int type()
            {
                return type;
            }

            @Override
            public long startNode()
            {
                return startNode;
            }

            @Override
            public long endNode()
            {
                return endNode;
            }

            @Override
            public long otherNode( long nodeId )
            {
                return startNode == nodeId ? endNode : startNode;
            }

            @Override
            public Cursor<PropertyItem> properties()
            {
                return propertyCursor;
            }

            @Override
            public Cursor<PropertyItem> property( final int propertyKeyId )
            {
                return new Cursor<PropertyItem>()
                {
                    Cursor<PropertyItem> cursor = properties();

                    @Override
                    public boolean next()
                    {
                        while ( cursor.next() )
                        {
                            if ( cursor.get().propertyKeyId() == propertyKeyId )
                            {
                                return true;
                            }
                        }

                        return false;
                    }

                    @Override
                    public void close()
                    {
                        cursor.close();
                    }

                    @Override
                    public PropertyItem get()
                    {
                        return cursor.get();
                    }
                };
            }
        } );
    }

    public static PrimitiveIntSet labels( final int... labels )
    {
        return PrimitiveIntCollections.asSet( labels );
    }

    public static Cursor<PropertyItem> asPropertyCursor( final DefinedProperty... properties )
    {
        return cursor( map( StubCursors::asPropertyItem, Arrays.asList( properties ) ) );
    }

    private static PropertyItem asPropertyItem( final DefinedProperty property )
    {
        return new PropertyItem()
        {
            @Override
            public int propertyKeyId()
            {
                return property.propertyKeyId();
            }

            @Override
            public Object value()
            {
                return property.value();
            }
        };
    }

    @SafeVarargs
    public static <T> Cursor<T> cursor( final T... items )
    {
        return cursor( Iterables.asIterable( items ) );
    }

    public static <T> Cursor<T> cursor( final Iterable<T> items )
    {
        return new Cursor<T>()
        {
            Iterator<T> iterator = items.iterator();

            T current;

            @Override
            public boolean next()
            {
                if ( iterator.hasNext() )
                {
                    current = iterator.next();
                    return true;
                }
                else
                {
                    return false;
                }
            }

            @Override
            public void close()
            {
                iterator = items.iterator();
                current = null;
            }

            @Override
            public T get()
            {
                if ( current == null )
                {
                    throw new IllegalStateException();
                }

                return current;
            }
        };
    }
}
