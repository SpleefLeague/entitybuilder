/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.spleefleague.entitybuilder;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author jonas
 */
public class NestedListSerialization {
    
    public static class IntValues extends DBEntity implements DBSaveable, DBLoadable {
        
        @DBSave(fieldName = "primitive")
        @DBLoad(fieldName = "primitive")
        private int primitive;
        @DBSave(fieldName = "wrapper")
        @DBLoad(fieldName = "wrapper")
        private Integer wrapper;
    }
    
    public static class ParentDocument extends DBEntity implements DBSaveable, DBLoadable {
        
        @DBSave(fieldName = "values")
        @DBLoad(fieldName = "values")
        private List<IntValues> values;
    }
    
    @Test
    public void testInt() {
        IntValues iv = new IntValues();
        iv.primitive = 3;
        iv.wrapper = 4;
        ParentDocument parent = new ParentDocument();
        parent.values = new ArrayList<>();
        parent.values.add(iv);
        Document doc = EntityBuilder.serialize(parent).get("$set", Document.class);
        ParentDocument r = EntityBuilder.deserialize(doc, ParentDocument.class);
        assertNotNull(r.values);
        assertEquals(r.values.size(), parent.values.size());
        for (int i = 0; i < parent.values.size(); i++) {
            assertEquals(parent.values.get(i).primitive, r.values.get(i).primitive);
            assertEquals(parent.values.get(i).wrapper, r.values.get(i).wrapper);
        }
    }
}
