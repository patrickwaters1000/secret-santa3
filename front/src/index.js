import React, { Component } from "react";
import ReactDOM from "react-dom";

var handle = null;

var state = null;

const event = (route, body) => {
    fetch(route, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=utf-8' },
        body: JSON.stringify(body)
    }).then(resp => {
        return resp.json();
    }).then(newState => {
        handle.setState(newState);
    });
};

const NameButton = (name, clickable) => {
    let onClick;
    if (clickable) {
        onClick = () => { event("ready", name); };
    } else {
        onClick = () => {};
    }
    return React.createElement(
        "button",
        {
            onClick: onClick,
            style: {
	        maxWidth: "100px",
	        width: "100px",
            }
        },
        name
    );
};

const NameButtons = (state) => {
    let { name, names, ready } = state;
    if (name) {
        return [];
    }
    let buttons = names.map( n => NameButton(n, !ready.includes(n)));
    return [React.createElement(
        "div",
        {},
        React.createElement(
            "h1",
            { font: "arial" },
            "Who are you?"),
        ...buttons
    )];
};

const ReadyUsers = (state) => {
    let { ready, giftAssignments } = state;
    if (giftAssignments) {
        return [];
    }
    let lines = ready.map(name => React.createElement(
        "h1", {}, `${name} is ready!`
    ));
    return [React.createElement(
        "div",
        {},
        ...lines
    )];
};

const GiftAssignments = (state) => {
    let { giftAssignments } = state;
    if (!giftAssignments) {
        return [];
    }
    let lines = giftAssignments.map(name => React.createElement(
            "h1", {}, name
    ));
    return [React.createElement(
        "div",
        {},
        React.createElement("h1", {}, "Your Secret Santa assignments are:"),
        ...lines
    )];
};

class Page extends React.Component {
    constructor(props) {
        super(props);
        handle = this;
    };

    render() {
        let s = this.state;
        if (!s) { return React.createElement("div"); }
        return React.createElement(
            "div",
            {},
            ...NameButtons(s),
            ...ReadyUsers(s),
            ...GiftAssignments(s)
        );
    }
}

window.addEventListener("DOMContentLoaded", () => {
    const div = document.getElementById("main");
    const page = React.createElement(Page, {
        name: null,
        names: [],
        ready: [],
        giftAssignments: null,
    });
    ReactDOM.render(page, div);
    event("poll", null);
    setInterval(
        () => { event('poll', handle.state.name); },
        1000
    );
});
