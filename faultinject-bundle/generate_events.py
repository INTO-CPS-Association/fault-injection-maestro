import xml.etree.ElementTree as gfg 


def GenerateXML(fileName, no_events) : 
	
    root = gfg.Element("events") 
	
    inittime = 30
    for i in range(no_events):
        m1 = gfg.Element("event") 
        m1.set("id", str(i+1))
        m1.set("timeStep", str(inittime))
        inittime += 0.1
        root.append (m1) 
        
        b1 = gfg.SubElement(m1, "variable") 
        b1.set("valRef", "3")
        b1.set("type", "real")
        b1.set("newVal", "2.1")
	
    tree = gfg.ElementTree(root) 
	
    with open (fileName, "wb") as files : 
        tree.write(files) 

# Driver Code 
if __name__ == "__main__": 
    GenerateXML("Catalog.xml", 201) 

