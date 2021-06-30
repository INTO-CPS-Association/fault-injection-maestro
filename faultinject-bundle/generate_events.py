import xml.etree.ElementTree as gfg 


def GenerateXML(fileName, no_events, step_size) : 
	
    root = gfg.Element("events") 
	
    inittime = 10.0
    for i in range(no_events):
        m1 = gfg.Element("event") 
        m1.set("id", str(i+1))
        m1.set("timeStep", str(round(inittime,1)))
        inittime += step_size
        root.append (m1) 
        
        b1 = gfg.SubElement(m1, "variable") 
        b1.set("valRef", "11")
        b1.set("type", "real")
        b1.set("newVal", "50.0")
	
    tree = gfg.ElementTree(root) 
	
    with open (fileName, "wb") as files : 
        tree.write(files) 

# Driver Code 
if __name__ == "__main__": 
    nr_of_events = 19
    step_size = 0.1
    total = nr_of_events/step_size + 1
    print("Generating a total of events: ",total)
    GenerateXML("Catalog.xml", int(total), step_size) 

