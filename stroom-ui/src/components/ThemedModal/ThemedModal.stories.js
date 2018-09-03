/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';

import { storiesOf, addDecorator } from '@storybook/react';
import { compose, withState } from 'recompose';
import { Header, Button } from 'semantic-ui-react';

import { ReduxDecoratorWithInitialisation, ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { ThemedModal, ThemedConfirm } from '.';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const withModalOpen = withState('modalIsOpen', 'setModalIsOpen', false);

let TestModal = ({ modalIsOpen, setModalIsOpen }) => (
  <React.Fragment>
    <ThemedModal
      open={modalIsOpen}
      header={<Header className="header" content="This is the header" />}
      content={<div>Maybe put something helpful in here</div>}
      actions={
        <React.Fragment>
          <Button content="Nothing" onClick={() => setModalIsOpen(false)} />
          <Button content="Something" onClick={() => setModalIsOpen(false)} />
        </React.Fragment>
      }
      onClose={() => setModalIsOpen(false)}
    />
    <Button onClick={() => setModalIsOpen(!modalIsOpen)} content="Open" />
  </React.Fragment>
);

TestModal = withModalOpen(TestModal);

const CONFIRM_STATE = {
  UNUSED: 'unused',
  CONFIRMED: 'confirmed',
  CANCELLED: 'cancelled',
};
const withWasConfirmed = withState('isConfirmed', 'setIsConfirmed', CONFIRM_STATE.UNUSED);

const enhanceConfirm = compose(withModalOpen, withWasConfirmed);

let TestConfirm = ({
  modalIsOpen, setModalIsOpen, isConfirmed, setIsConfirmed,
}) => (
  <React.Fragment>
    <ThemedConfirm
      open={modalIsOpen}
      question="Are you sure about this?"
      details="Because...nothing will really happen anyway"
      onConfirm={() => {
        setIsConfirmed(CONFIRM_STATE.CONFIRMED);
        setModalIsOpen(false);
      }}
      onCancel={() => {
        setIsConfirmed(CONFIRM_STATE.CANCELLED);
        setModalIsOpen(false);
      }}
      onClose={() => setModalIsOpen(false)}
    />
    <Button onClick={() => setModalIsOpen(!modalIsOpen)} content="Check" />
    <div>Current State: {isConfirmed}</div>
  </React.Fragment>
);

TestConfirm = enhanceConfirm(TestConfirm);

storiesOf('Themed Modal', module)
  .addDecorator(ReduxDecorator)
  .add('Test Modal', () => <TestModal />)
  .add('Test Confirm', () => <TestConfirm />);
