# Battery

Each power source manages a battery that can be used to store and supply energy to the multiplexer. Each battery has three states: Idle, Charging and Supplying. Each battery has a policy that dictates it's behavior. There are three policies: carbon based, power demand based, and cyclic.

## Input
Battery properties are specified in JSON

```json
"battery": {
	"description": "Battery used by the power source",
	"type": "object",
	"properties": {
		"policy": {
			"description": "The policy type of the battery",
			"type": "string"
		},
		"policyThreshold": {
			"description": "Threshold value for the policy",
			"type": "number"
		},
		"capacity": {
			"description": "The maximum energy capacity of the battery",
			"type": "number",
		},
		"chargeRate": {
			"description": "The charging rate of the battery",
			"type": "number",
		}
},
"required": [
	"policy",
	"policyThreshold",
	"capacity",
	"chargeRate"
]
```
They are written in the topology files, there is one for each cluster.
```json
{
  "clusters": [
    {
      "name": "C01",
      "hosts": [
        {
          "name": "H01",
          "cpu": {
            "coreCount": 12,
            "coreSpeed": 3300
          },
          "memory": {
            "memorySize": 140457600000
          }
        }
      ],
      "battery": {
        "policy": "carbon",
        "policyThreshold": 100,
        "capacity": 300000,
        "chargeRate": 300
      }
    }
  ]
} 
```
