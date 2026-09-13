"""Generates the straight-line fixture processes.

Every fixture is the same shape — start event, a few tasks in a row, end event — so the
BPMN is generated rather than copy-pasted four times: the diagram interchange alone is
two thirds of each file and is exactly where a hand-edited copy goes wrong.

Run from anywhere:

    python3 backend/app/tools/generate-fixture-bpmn.py

It rewrites every `*.bpmn` of the fixture case. Edit a process's task list below rather
than the generated XML; a hand edit is overwritten on the next run.
"""

import os

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..",
    "src/main/resources/config/case/claude/1-0-0/bpmn",
)

FIXTURES = {
    "claude-fixture-ask": (
        "Claude fixture: ask a question",
        [
            ("serviceTask", "askClaude", "Ask Claude"),
            ("userTask", "showAnswer", "Show result: the answer"),
        ],
    ),
    "claude-fixture-case-data": (
        "Claude fixture: case data in the prompt",
        [
            ("serviceTask", "askAboutCase", "Ask Claude about the case"),
            ("userTask", "showAnswer", "Show result: the answer"),
        ],
    ),
    "claude-fixture-result-mapping": (
        "Claude fixture: map a JSON answer",
        [
            ("serviceTask", "askForJson", "Ask Claude for JSON"),
            ("userTask", "showMapping", "Show result: the mapped values"),
        ],
    ),
    "claude-fixture-document": (
        "Claude fixture: ask about a document",
        [
            ("serviceTask", "askAboutDocument", "Ask Claude about the document"),
            ("userTask", "showAnswer", "Show result: the answer"),
        ],
    ),
}

TASK_WIDTH, TASK_HEIGHT, GAP = 150, 80, 50
START_X, LANE_Y = 180, 90
EVENT_SIZE = 36


def build(process_id, name, tasks):
    nodes = [("startEvent", "StartEvent_1", "Start")] + tasks + [("endEvent", "EndEvent_1", "End")]
    flows = [(f"Flow_{i}", nodes[i][1], nodes[i + 1][1]) for i in range(len(nodes) - 1)]

    body = []
    for index, (kind, node_id, label) in enumerate(nodes):
        incoming = f"      <bpmn:incoming>Flow_{index - 1}</bpmn:incoming>\n" if index > 0 else ""
        outgoing = f"      <bpmn:outgoing>Flow_{index}</bpmn:outgoing>\n" if index < len(nodes) - 1 else ""
        body.append(
            f'    <bpmn:{kind} id="{node_id}" name="{label}">\n{incoming}{outgoing}    </bpmn:{kind}>'
        )
    for flow_id, source, target in flows:
        body.append(f'    <bpmn:sequenceFlow id="{flow_id}" sourceRef="{source}" targetRef="{target}" />')

    # Laid out left to right; events are centred on the lane, tasks sit on it.
    shapes, x_positions = [], []
    x = START_X
    for kind, node_id, _ in nodes:
        if kind.endswith("Event"):
            width = height = EVENT_SIZE
            y = LANE_Y + (TASK_HEIGHT - EVENT_SIZE) // 2
        else:
            width, height, y = TASK_WIDTH, TASK_HEIGHT, LANE_Y
        shapes.append(
            f'      <bpmndi:BPMNShape id="{node_id}_di" bpmnElement="{node_id}">\n'
            f'        <dc:Bounds x="{x}" y="{y}" width="{width}" height="{height}" />\n'
            f"      </bpmndi:BPMNShape>"
        )
        x_positions.append((x, width))
        x += width + GAP

    centre_y = LANE_Y + TASK_HEIGHT // 2
    edges = []
    for index, (flow_id, _, _) in enumerate(flows):
        source_x = x_positions[index][0] + x_positions[index][1]
        target_x = x_positions[index + 1][0]
        edges.append(
            f'      <bpmndi:BPMNEdge id="{flow_id}_di" bpmnElement="{flow_id}">\n'
            f'        <di:waypoint x="{source_x}" y="{centre_y}" />\n'
            f'        <di:waypoint x="{target_x}" y="{centre_y}" />\n'
            f"      </bpmndi:BPMNEdge>"
        )

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_{process_id}" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="{process_id}" name="{name}" processType="None" isClosed="false" isExecutable="true">
{chr(10).join(body)}
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_{process_id}">
    <bpmndi:BPMNPlane id="BPMNPlane_{process_id}" bpmnElement="{process_id}">
{chr(10).join(shapes)}
{chr(10).join(edges)}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
"""


if __name__ == "__main__":
    for process_id, (name, tasks) in FIXTURES.items():
        path = os.path.join(OUTPUT_DIR, f"{process_id}.bpmn")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(build(process_id, name, tasks))
        print(f"wrote {os.path.relpath(path)}")
