/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.spleefleague.entitybuilder;

import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author jonas
 */
public class NestedEntitySerialization {
    
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
        private IntValues values;
    }
    
    @Test
    public void testInt() {
        IntValues iv = new IntValues();
        iv.primitive = 3;
        iv.wrapper = 4;
        ParentDocument parent = new ParentDocument();
        parent.values = iv;
        Document doc = EntityBuilder.serialize(parent).get("$set", Document.class);
        ParentDocument r = EntityBuilder.deserialize(doc, ParentDocument.class);
        assertNotNull(r.values);
        assertEquals(iv.primitive, r.values.primitive);
        assertEquals(iv.wrapper, r.values.wrapper);
    }
}
